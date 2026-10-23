# Results

Measured on 2026-10-23 on a Samsung SM-S928B (Android 16/API 36, ARM64) with the release Android-test APK. No emulator
or low-battery suppression. See the [README](README.md) for the methodology.

| Fixtures | Benchmarks | Battery / skin °C before | Battery / skin °C after |
|---------:|-----------:|-------------------------:|------------------------:|
|       10 |         15 |              29.4 / 33.0 |             30.5 / 35.7 |
|       25 |         15 |              28.2 / 33.4 |             30.6 / 36.1 |
|       50 |         15 |              19.7 / 24.2 |             24.5 / 29.1 |
|      100 |         15 |              25.1 / 29.7 |             26.7 / 32.2 |

## Cold singleton

Median nanoseconds per measured invocation.

| Implementation     |        10 |        25 |        50 |        100 |
|--------------------|----------:|----------:|----------:|-----------:|
| Stitch precompiled |    279.79 |    643.51 |  1,141.22 |   2,734.82 |
| Stitch runtime     |  3,056.98 |  8,615.06 | 17,094.01 |  35,973.52 |
| Dagger             |    335.39 |    744.79 |  1,457.90 |   3,642.17 |
| Koin               | 17,922.77 | 43,218.67 | 86,800.44 | 187,182.74 |
| Metro              |    262.25 |    706.53 |  1,381.31 |   4,606.65 |

## Warm singleton

Median nanoseconds per measured invocation.

| Implementation     |     10 |     25 |     50 |    100 |
|--------------------|-------:|-------:|-------:|-------:|
| Stitch precompiled |   1.63 |   1.55 |   1.62 |   1.61 |
| Stitch runtime     |  65.97 |  65.40 |  66.40 |  65.02 |
| Dagger             |   2.11 |   2.12 |   2.08 |   4.29 |
| Koin               | 624.53 | 625.91 | 651.30 | 659.45 |
| Metro              |   3.88 |   4.17 |   4.21 |   4.18 |

## Factory

Median nanoseconds per measured invocation.

| Implementation     |        10 |        25 |        50 |        100 |
|--------------------|----------:|----------:|----------:|-----------:|
| Stitch precompiled |    145.87 |    388.36 |    641.35 |   1,115.55 |
| Stitch runtime     |  1,269.60 |  3,692.22 |  8,472.63 |  18,213.90 |
| Dagger             |    218.30 |    341.90 |    660.99 |   1,206.20 |
| Koin               | 14,089.14 | 33,984.55 | 66,306.36 | 136,048.92 |
| Metro              |    138.58 |    332.62 |    638.04 |   1,373.17 |

## Factory allocations

Median allocations per invocation, measured separately from timing (5 allocation measurements per case).

| Implementation     |      10 |      25 |       50 |      100 |
|--------------------|--------:|--------:|---------:|---------:|
| Stitch precompiled |  10.000 |  25.000 |   50.000 |  100.001 |
| Stitch runtime     |  45.000 | 110.001 |  213.001 |  415.003 |
| Dagger             |  10.000 |  25.000 |   50.000 |  100.001 |
| Koin               | 250.001 | 625.003 | 1250.005 | 2500.010 |
| Metro              |  10.000 |  25.000 |   50.000 |  100.001 |

The precompiled factory paths allocate about N objects for an N-node chain. The runtime frameworks also allocate
resolution state.

## Warm singleton before and after #249

Median nanoseconds per measured invocation for Stitch precompiled, on the same device and benchmark module. The before
run was measured on 2026-10-21. Both runs injected N fields per target, while the tables above inject one binding backed
by an N-deep chain.

| Commit             |    10 |    25 |     50 |    100 |
|--------------------|------:|------:|-------:|-------:|
| `7305517` (before) | 34.62 | 88.02 | 175.03 | 438.21 |
| `d2677e3` (after)  | 12.06 | 28.54 |  58.71 | 117.20 |

## Raw data

- [10 fixtures](../docs/benchmarks/v1.1.0/injection-performance/injection-performance-10-fixtures.json)
- [25 fixtures](../docs/benchmarks/v1.1.0/injection-performance/injection-performance-25-fixtures.json)
- [50 fixtures](../docs/benchmarks/v1.1.0/injection-performance/injection-performance-50-fixtures.json)
- [100 fixtures](../docs/benchmarks/v1.1.0/injection-performance/injection-performance-100-fixtures.json)
