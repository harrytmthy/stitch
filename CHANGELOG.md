# Changelog

All notable changes to this project will be documented in this file.

## [1.0.3] - 2026-10-19

### Fixed

- **Nullable cached bindings:** Generated graphs now return `null` on every read of a cached nullable binding. ([#234](https://github.com/harrytmthy/stitch/issues/234))
- **Child scope names:** The generated `createInjectorForChildScope()` now matches scope names case-insensitively, as documented. ([#236](https://github.com/harrytmthy/stitch/issues/236))
- **Qualifier validation:** The generated `get()` now throws when the requested qualifier has no matching binding, including for types with a single unqualified binding. ([#237](https://github.com/harrytmthy/stitch/issues/237))

## [1.0.2] - 2026-10-18

### Fixed

- **Scoped factory resolution:** A scoped factory now resolves its own scoped bindings after resolving a singleton or a binding owned by a parent scope. ([#231](https://github.com/harrytmthy/stitch/issues/231))

## [1.0.1] - 2026-10-14

### Fixed

- **Kotlin Multiplatform support:** Added JavaScript (browser and Node.js) and iOS artifacts with shared-source KSP generation. Removed calls to the API 24 `HashMap.putIfAbsent` method. ([#222](https://github.com/harrytmthy/stitch/issues/222))

### Changed

- **Yarn lockfile validation:** Missing or outdated Yarn lockfiles now fail validation, with a pre-push check for the main build. ([#224](https://github.com/harrytmthy/stitch/issues/224))
- **Build tooling:** Updated dependencies and build tools, including Kotlin 2.4.20, Atomicfu 0.33.0, AGP 9.3.3, and Gradle 9.8.0. ([#219](https://github.com/harrytmthy/stitch/issues/219))

## [1.0.0] - 2026-04-27

### Added
- 🎉 First stable release published to Maven Central.
- Precompiled path for top-tier injection performance.
- Runtime registration path for dynamic module registration.
- Parent-child scope dependencies in the runtime path.
- Custom qualifier support in the precompiled path.
- Migration guide for Dagger 2, Hilt, and Koin.
- Benchmark charts and measurement docs for injection performance, APK size impact, and build-time impact.
