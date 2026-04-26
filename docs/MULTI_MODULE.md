# Multi-Module Setup for the Precompiled Path

For the precompiled path, Stitch has two module roles:

- **Root module**: the module that depends on other modules and has `@StitchRoot`-annotated class
- **Contributor modules**: other modules that contain annotations

In practice, a typical setup looks like this:

- `:app` = root module
- `:core`, `:feature:home`, `:feature:profile` = contributor modules

## 1. Root module setup

```kotlin
plugins {
    id("com.google.devtools.ksp") version "2.3.6"
}

dependencies {
    implementation("io.github.harrytmthy:stitch:1.0.0")
    compileOnly("io.github.harrytmthy:stitch-annotations:1.0.0")
    ksp("io.github.harrytmthy:stitch-ksp:1.0.0")
}
```

and annotate a class with `@StitchRoot`:

```kotlin
import io.github.harrytmthy.stitch.annotations.StitchRoot

@StitchRoot
class SampleApp
```

After building, initialize the generated root graph at your app bootstrap point:

```kotlin
StitchInjector.init(StitchSingletonGraph())
```

## 2. Contributor module setup

```kotlin
plugins {
    id("com.google.devtools.ksp") version "2.3.6"
    id("io.github.harrytmthy.stitch") version "1.0.0"
}

dependencies {
    implementation("io.github.harrytmthy:stitch:1.0.0")
    compileOnly("io.github.harrytmthy:stitch-annotations:1.0.0")
    ksp("io.github.harrytmthy:stitch-ksp:1.0.0")
}
```

If you don't want to use Stitch Gradle plugin, add this to each module's `build.gradle.kts`:

```kotlin
ksp.arg("stitch.moduleName", project.path)
```

Or manually supply `stitch.moduleName`, ensuring it is globally unique.

## 3. Sharing scopes across modules

Each scope represents a global graph, and its declarations can be done in any module:

```kotlin
@Scope
@Retention(AnnotationRetention.BINARY)
annotation class ActivityScope

@Scope
@DependsOn(ActivityScope::class)
@Retention(AnnotationRetention.BINARY)
annotation class FragmentScope
```

To prevent all modules from depending on a single module that provides the custom scope annotations,
Stitch provides a _decentralized way_ to access any scope via `@Scope("...")`:

```kotlin
@Scope(name = "ActivityScope") // Case-insensitive
class LoggerImpl @Inject constructor() : Logger
```

Any uppercase/lowercase combination of "ActivityScope" points to the same graph:
- `@Scope("activityScope")`
- `@Scope("aCtiviTyScoPE")`
- Or with whitespace: `@Scope("  ActivityScope")`

You can shorten the name using the same parameter at the declaration site:

```kotlin
@Scope(name = "activity")
@Retention(AnnotationRetention.BINARY)
annotation class ActivityScope

// Then later use the shortened version (also case-insensitive)
@Scope(name = "activity")
class LoggerImpl @Inject constructor() : Logger
```
