"""Builds and runs the injection benchmark on a connected device, one fixture size at a time.

Usage: python3 benchmark/run_benchmark.py 10 25 50 100

Set ANDROID_SERIAL when more than one device is connected. A run that cannot start (device missing,
locked, or too hot) is retried every 5 seconds, so the phone can be unplugged to cool down.
"""
import os
import re
import shutil
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
APK = REPO / "benchmark/build/outputs/apk/androidTest/release/benchmark-release-androidTest.apk"
RESULTS = REPO / "benchmark/results"
PACKAGE = "com.harrytmthy.stitch.benchmark.test"
MAX_CELSIUS = 35
BATTERY_TYPE, SKIN_TYPE = 2, 3
original_stay_on = None


class NotReady(Exception):
    pass


def adb_path():
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if sdk and (Path(sdk) / "platform-tools/adb").is_file():
        return str(Path(sdk) / "platform-tools/adb")
    return shutil.which("adb") or sys.exit("adb not found. Set ANDROID_HOME or add adb to PATH.")


ADB = adb_path()


def run(*args):
    if subprocess.run([ADB, *args]).returncode:
        raise NotReady(f"adb {args[0]} failed")


def read(*args):
    result = subprocess.run([ADB, *args], capture_output=True, text=True)
    if result.returncode:
        raise NotReady(result.stderr.strip() or f"adb {args[0]} failed")
    return result.stdout


def temperature(label):
    raw = read("shell", "dumpsys", "thermalservice")
    current = raw.partition("Current temperatures from HAL:")[2].partition("Current cooling devices from HAL:")[0]
    readings = [(float(value), int(kind), name)
                for value, kind, name in re.findall(r"mValue=([\d.]+), mType=(\d+), mName=([^,]+)", current)]
    status = re.search(r"^Thermal Status: (\d+)", raw, re.M)
    battery = max((value for value, kind, _ in readings if kind == BATTERY_TYPE), default=None)
    skin = max((value for value, kind, _ in readings if kind == SKIN_TYPE), default=None)
    if status is None or battery is None or skin is None:
        raise RuntimeError("Cannot read the battery and skin temperatures from dumpsys thermalservice.")
    print(f"{label}: battery={battery}°C skin={skin}°C status={status.group(1)}", flush=True)
    if battery > MAX_CELSIUS or skin > MAX_CELSIUS or status.group(1) != "0":
        raise NotReady("Too hot")


def check_unlocked():
    keyguard = read("shell", "dumpsys", "window", "policy").partition("KeyguardServiceDelegate")[2]
    if not all(re.search(rf"^\s+{field}=false\s*$", keyguard, re.M) for field in ("showing", "inputRestricted")):
        raise NotReady("Device is locked")


def build(size):
    print(f"\nBuilding fixtures={size}", flush=True)
    command = ["./gradlew", ":benchmark:assembleReleaseAndroidTest", f"-Pfixture={size}", "--console=plain", "-q"]
    if subprocess.run(command, cwd=REPO).returncode:
        sys.exit(f"Build failed for fixtures={size}. The benchmark module needs Gradle on JDK 21 or newer.")


def measure(size):
    global original_stay_on
    if original_stay_on is None:
        original_stay_on = read("shell", "settings", "get", "global", "stay_on_while_plugged_in").strip()
    run("shell", "svc", "power", "stayon", "true")
    run("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    check_unlocked()
    run("install", "-r", "-t", str(APK))
    device_output = f"/sdcard/Download/stitch-benchmark/{datetime.now():%Y%m%d-%H%M%S}/fixtures-{size}"
    run("shell", "mkdir", "-p", device_output)
    check_unlocked()
    temperature("Before measurement")

    command = [ADB, "shell", "am", "instrument", "-w", "-r",
               "-e", "androidx.benchmark.output.enable", "true",
               "-e", "additionalTestOutputDir", device_output,
               f"{PACKAGE}/androidx.benchmark.junit4.AndroidBenchmarkRunner"]
    lines = []
    with subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True) as process:
        for line in process.stdout:
            print(line, end="", flush=True)
            lines.append(line)
        exit_code = process.wait()
    try:
        temperature("After measurement")
    except NotReady:
        pass

    # adb can exit successfully even when instrumentation tests fail.
    log = "".join(lines)
    if exit_code or "INSTRUMENTATION_CODE: -1" not in log or not re.search(r"OK \([1-9]\d* tests?\)", log):
        sys.exit(f"Tests did not report success for fixtures={size}. Device output remains at {device_output}")

    RESULTS.mkdir(parents=True, exist_ok=True)
    destination = RESULTS / f"benchmark-fixtures-{size}.json"
    temporary = destination.with_name(f".{destination.name}.part")
    run("pull", f"{device_output}/{PACKAGE}-benchmarkData.json", str(temporary))
    temporary.replace(destination)
    print(f"Saved {destination.relative_to(REPO)}", flush=True)


def main():
    sizes = [int(arg) for arg in sys.argv[1:] if arg.isdigit()]
    if not sizes or len(sizes) != len(sys.argv) - 1:
        sys.exit(__doc__)
    try:
        for size in sizes:
            build(size)
            while True:
                try:
                    measure(size)
                    break
                except NotReady as reason:
                    print(f"{reason}. Retrying in 5 seconds.", flush=True)
                    time.sleep(5)
    finally:
        if original_stay_on is not None:
            action = ["delete", "global", "stay_on_while_plugged_in"] if original_stay_on == "null" \
                else ["put", "global", "stay_on_while_plugged_in", original_stay_on]
            subprocess.run([ADB, "shell", "settings", *action])


if __name__ == "__main__":
    main()
