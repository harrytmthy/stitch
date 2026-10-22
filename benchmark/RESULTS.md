# Results

Measured on 2026-10-22 on a Samsung SM-S928B (Android 16/API 36, ARM64) with the release Android-test APK. No emulator
or low-battery suppression. See the [README](README.md) for the methodology.

| Fixtures | Benchmarks | Battery / skin °C before | Battery / skin °C after | Thermal status |
|---------:|-----------:|-------------------------:|------------------------:|---------------:|
|       10 |         13 |              30.9 / 32.9 |             31.1 / 34.6 |              0 |
|       25 |         13 |              31.4 / 32.6 |             31.8 / 35.0 |              0 |
|       50 |         13 |              31.8 / 35.0 |             32.9 / 36.8 |              0 |
|      100 |         13 |              32.1 / 33.0 |             32.7 / 35.7 |              0 |

## Fresh-graph singleton

Median nanoseconds per measured invocation.

| Implementation     |     10 |     25 |       50 |      100 |
|--------------------|-------:|-------:|---------:|---------:|
| Stitch precompiled | 354.52 | 873.44 | 1,559.44 | 3,470.69 |
| Dagger             | 322.80 | 862.34 | 1,772.81 | 4,352.45 |
| Metro              | 312.26 | 839.09 | 1,965.93 | 5,351.09 |

## Warm singleton

Median nanoseconds per measured invocation.

| Implementation     |       10 |        25 |        50 |       100 |
|--------------------|---------:|----------:|----------:|----------:|
| Stitch precompiled |    12.06 |     28.54 |     58.71 |    117.20 |
| Stitch runtime     |   868.96 |  2,276.21 |  5,098.48 | 11,586.10 |
| Dagger             |    17.92 |     40.00 |     83.65 |    366.29 |
| Koin               | 6,777.02 | 16,849.87 | 35,171.05 | 69,386.15 |
| Metro              |    39.51 |     88.99 |    228.20 |    566.41 |

## Factory

Median nanoseconds per measured invocation.

| Implementation     |        10 |        25 |        50 |        100 |
|--------------------|----------:|----------:|----------:|-----------:|
| Stitch precompiled |    170.26 |    402.20 |    656.24 |   1,406.22 |
| Stitch runtime     |  1,652.99 |  4,912.50 | 10,902.21 |  22,387.54 |
| Dagger             |    178.91 |    381.82 |    628.53 |   1,306.43 |
| Koin               | 13,419.50 | 36,589.90 | 72,239.64 | 152,202.51 |
| Metro              |    172.31 |    339.48 |    670.82 |   1,419.50 |

## Factory allocations

Median allocations per invocation, measured separately from timing (5 allocation measurements per case).

| Implementation     |      10 |      25 |       50 |      100 |
|--------------------|--------:|--------:|---------:|---------:|
| Stitch precompiled |  10.000 |  25.000 |   50.000 |  100.000 |
| Stitch runtime     |  45.001 | 110.001 |  213.002 |  415.007 |
| Dagger             |  10.000 |  25.000 |   50.000 |  100.001 |
| Koin               | 250.002 | 625.006 | 1250.007 | 2500.013 |
| Metro              |  10.000 |  25.000 |   50.000 |  100.001 |

The precompiled factory paths allocate about N objects for an N-node chain. The runtime frameworks also allocate
resolution state.

## Warm singleton before and after #249

Median nanoseconds per measured invocation for Stitch precompiled, on the same device and benchmark module. The before
run was measured on 2026-10-21.

| Commit             |    10 |    25 |     50 |    100 |
|--------------------|------:|------:|-------:|-------:|
| `7305517` (before) | 34.62 | 88.02 | 175.03 | 438.21 |
| `d2677e3` (after)  | 12.06 | 28.54 |  58.71 | 117.20 |

## Raw data

- [10 fixtures](results/benchmark-fixtures-10.json)
- [25 fixtures](results/benchmark-fixtures-25.json)
- [50 fixtures](results/benchmark-fixtures-50.json)
- [100 fixtures](results/benchmark-fixtures-100.json)
