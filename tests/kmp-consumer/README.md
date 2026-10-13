# Multiplatform consumer test

This standalone build resolves Stitch from Maven Local, never from a project dependency
or Maven Central. It checks runtime registration and real KSP-generated graphs from
shared Kotlin sources on JVM, JS, and iOS. A separate `bindings` module also checks
binding and scope contributions, aliases, qualifiers, and injected fields across a
common-metadata module boundary.

## Publish and test

From the repository root, append a local suffix to the release version in
`PublishingConventionPlugin`. Read the resulting project version and pass it
explicitly to the consumer, using the same isolated Maven repository:

```sh
maven_repo="$(mktemp -d)"
stitch_version="$(./gradlew :stitch:properties -PversionSuffix=-kmp-local --quiet --console=plain | sed -n 's/^version: //p')"

./gradlew publishToMavenLocal \
  -PversionSuffix=-kmp-local -Dmaven.repo.local="$maven_repo" \
  --init-script tests/kmp-consumer/local-publication.init.gradle.kts \
  --no-configuration-cache

./gradlew -p tests/kmp-consumer -PstitchVersion="$stitch_version" -Dmaven.repo.local="$maven_repo" \
  jvmTest jsNodeTest jsBrowserTest compileKotlinIosArm64 compileKotlinIosSimulatorArm64 compileKotlinIosX64 \
  --no-configuration-cache

./gradlew -p tests/kmp-consumer -PstitchVersion="$stitch_version" -Dmaven.repo.local="$maven_repo" iosSimulatorArm64Test \
  --no-configuration-cache
```

The local-publication init script disables signing only for the local publication command.
It does not change release signing. The suffix defaults to empty for normal releases.
The consumer imports Kotlin and KSP versions from the root catalog, but not its
Stitch version: that entry selects the published dependency used by the playground.
JS browser tests require Chrome. iOS execution requires
an Apple Silicon Mac, full Xcode, and an installed iOS Simulator runtime. On an Intel Mac,
use `iosX64Test` instead. Compiling an iOS KLIB does not demonstrate simulator execution.

Both `stitch` and `stitch-annotations` must publish root metadata plus `-jvm`, `-js`,
`-iosarm64`, `-iossimulatorarm64`, and `-iosx64` artifacts. The JVM-only `stitch-ksp`
publication is expected because processors run on the build host.

## Shared-source generation

Use KSP **2.3.12 or newer**. Earlier versions can miss contribution declarations from
Kotlin libraries when processing common metadata or Native targets
([upstream correction](https://github.com/google/ksp/releases/tag/2.3.12)).

`build.gradle.kts` configures `kspCommonMainMetadata` once, adds its generated sources to
`commonMain`, and orders target compilation after generation. The same generated graph
is then compiled for every target. Use Stitch's annotations in common code, not
`javax.inject` annotations.

Target-specific `kspJvm`, `kspJs`, or `kspIosArm64` generation is a separate layout.
Do not combine it with common metadata generation of the same graph declarations.
