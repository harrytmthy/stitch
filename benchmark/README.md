# Benchmark

Injection benchmarks comparing Stitch with Dagger, Koin and Metro. Each fixture size N generates N bindings for every
implementation. The latest measurements are in [RESULTS.md](RESULTS.md).

## Running

Run Gradle with JDK 21 or newer, because Metro's Gradle plugin requires it. Android Studio's bundled JBR works. With an
older JDK, the build skips this module.

```bash
./gradlew :benchmark:connectedReleaseAndroidTest -Pfixture=10
```

`-Pfixture` accepts any size and defaults to 10. Results are written as AndroidX Benchmark JSON under
`benchmark/build/outputs/connected_android_test_additional_output/`.

To run several sizes in order with a temperature gate:

```bash
python3 benchmark/run_benchmark.py 10 25 50 100
```

The script keeps the screen on while plugged in, then restores the original setting. A run that cannot start is retried
every 5 seconds. Each result is saved to `results/`. Set `ANDROID_SERIAL` when more than one device is connected.

## Methodology

Each benchmark runs 20 warmups and 100 measurements. A measurement may batch multiple invocations, so these are not 100
independent process runs. Fixture sizes run in order, one suite at a time. To avoid thermal throttling, battery and skin
must both be at or below 35°C before measurement.

- Cold singleton resolves one binding backed by an N-deep dependency chain using a new graph or container. Graph and
  container setup are excluded from measurement. This is not cold-process startup.
- Warm singleton injects one binding, backed by an N-deep chain, into a target using an already-warm graph. The
  precompiled graphs are primed once before measuring. The runtime path and Koin warm up during the harness warmup.
- Factory injects one root whose dependency chain constructs N objects. Graph setup is outside timing.
