#!/bin/bash
# apk-size-report.sh
# Usage: ./apk-size-report.sh [fixture_count]
# Example: ./apk-size-report.sh 10

FIXTURE=${1:-10}
APK_DIR="samples/app/build/outputs/apk"
OUT_DIR="samples/app/build/outputs/apk-size-report"

# ---------------------------------------------------------------------------
# Step 1: Build all flavors
# ---------------------------------------------------------------------------

echo "=== Building all flavors (${FIXTURE} fixtures) ==="
echo ""

./gradlew clean \
  :app:assembleNoneRelease \
  :app:assembleKoinRelease \
  :app:assembleDaggerRelease \
  :app:assembleStitchRelease \
  --stacktrace \
  --no-configuration-cache \
  -PexcludeOtherModules=true \
  -Pfixture="$FIXTURE"

if [ $? -ne 0 ]; then
  echo ""
  echo "ERROR: Gradle build failed. Aborting report."
  exit 1
fi

# ---------------------------------------------------------------------------
# Step 2: Measure and report
# ---------------------------------------------------------------------------

echo ""
echo "=== APK Size Comparison (${FIXTURE} fixtures) ==="
echo ""

mkdir -p "$OUT_DIR"

JSON_OUT="$OUT_DIR/apk-size-${FIXTURE}-fixtures.json"
MD_OUT="$OUT_DIR/apk-size-${FIXTURE}-fixtures.md"

# Collect sizes into plain variables (bash 3.2 compatible)
get_size() {
  local apk="$APK_DIR/$1/release/app-$1-release-unsigned.apk"
  if [ -f "$apk" ]; then
    stat -f%z "$apk"
  else
    echo "WARNING: APK not found for flavor '$1' at $apk" >&2
    echo 0
  fi
}

size_none=$(get_size "none")
size_koin=$(get_size "koin")
size_dagger=$(get_size "dagger")
size_stitch=$(get_size "stitch")

baseline=$size_none

kb_none=$(awk   "BEGIN { printf \"%.1f\", $size_none/1024 }")
kb_koin=$(awk   "BEGIN { printf \"%.1f\", $size_koin/1024 }")
kb_dagger=$(awk "BEGIN { printf \"%.1f\", $size_dagger/1024 }")
kb_stitch=$(awk "BEGIN { printf \"%.1f\", $size_stitch/1024 }")

delta_koin=$((size_koin - baseline))
delta_dagger=$((size_dagger - baseline))
delta_stitch=$((size_stitch - baseline))

delta_kb_koin=$(awk   "BEGIN { printf \"%.1f\", ($size_koin   - $baseline)/1024 }")
delta_kb_dagger=$(awk "BEGIN { printf \"%.1f\", ($size_dagger - $baseline)/1024 }")
delta_kb_stitch=$(awk "BEGIN { printf \"%.1f\", ($size_stitch - $baseline)/1024 }")

# Print to console
echo "none:   $size_none bytes ($kb_none KB) [baseline]"
echo "koin:   $size_koin bytes ($kb_koin KB) [+$delta_koin bytes / +$delta_kb_koin KB]"
echo "dagger: $size_dagger bytes ($kb_dagger KB) [+$delta_dagger bytes / +$delta_kb_dagger KB]"
echo "stitch: $size_stitch bytes ($kb_stitch KB) [+$delta_stitch bytes / +$delta_kb_stitch KB]"

# Write JSON
cat > "$JSON_OUT" << EOF
{
  "apk_size_comparison": {
    "title": "APK Size Impact - ${FIXTURE} Fixtures",
    "unit": "bytes",
    "note": "Release unsigned APKs. 'none' is the no-DI baseline.",
    "fixture_count": ${FIXTURE},
    "results": [
      {
        "flavor": "none",
        "bytes": $size_none,
        "kb": $kb_none,
        "delta_bytes": 0,
        "delta_kb": 0.0,
        "is_baseline": true
      },
      {
        "flavor": "koin",
        "bytes": $size_koin,
        "kb": $kb_koin,
        "delta_bytes": $delta_koin,
        "delta_kb": $delta_kb_koin,
        "is_baseline": false
      },
      {
        "flavor": "dagger",
        "bytes": $size_dagger,
        "kb": $kb_dagger,
        "delta_bytes": $delta_dagger,
        "delta_kb": $delta_kb_dagger,
        "is_baseline": false
      },
      {
        "flavor": "stitch",
        "bytes": $size_stitch,
        "kb": $kb_stitch,
        "delta_bytes": $delta_stitch,
        "delta_kb": $delta_kb_stitch,
        "is_baseline": false
      }
    ]
  }
}
EOF

# Write Markdown
cat > "$MD_OUT" << EOF
# APK Size Impact — ${FIXTURE} Fixtures

| Flavor | Size (bytes) | Size (KB) | Delta (bytes) | Delta (KB) |
|--------|-------------|-----------|---------------|------------|
| none *(baseline)* | $size_none | $kb_none | — | — |
| koin   | $size_koin   | $kb_koin   | +$delta_koin   | +$delta_kb_koin   |
| dagger | $size_dagger | $kb_dagger | +$delta_dagger | +$delta_kb_dagger |
| stitch | $size_stitch | $kb_stitch | +$delta_stitch | +$delta_kb_stitch |

> Release unsigned APKs, ${FIXTURE} fixture classes.
EOF

echo ""
echo "Reports written to:"
echo "- JSON: $JSON_OUT"
echo "- Markdown: $MD_OUT"