# Migration Guide

- [Migrating from Dagger 2 / Hilt](#migrating-from-dagger-2--hilt)
- [Migrating from Koin](#migrating-from-koin)

---

## Before You Start

Stitch has **two paths**:

- **Precompiled path**: Generated graphs, field injection, and explicit scope hierarchy via annotations
- **Runtime registration path**: Dynamic module registration via `module { ... }` and runtime scopes

Pick the path that matches what you want to migrate:

- Coming from **Dagger 2 / Hilt**: usually migrate to the **precompiled path**
- Coming from **Koin**: usually migrate to the **runtime registration path**
- Later, you can use both paths in one project

## One-time Setup

### Runtime registration path

Add the core dependency:

```kotlin
dependencies {
    implementation("io.github.harrytmthy:stitch:1.0.0-rc01")
}
```

Then register modules at your app startup or feature bootstrap point:

```kotlin
Stitch.register(coreModule, homeModule)
```

This path does **not** require KSP.

### Precompiled path

Add the runtime, annotation surface, and KSP processor:

```kotlin
dependencies {
    implementation("io.github.harrytmthy:stitch:1.0.0-rc01")
    compileOnly("io.github.harrytmthy:stitch-annotations:1.0.0-rc01")
    ksp("io.github.harrytmthy:stitch-ksp:1.0.0-rc01")
}
```

Annotate any class in the root module with `@StitchRoot`:

```kotlin
@StitchRoot
class AppGraphRoot
```

Build once, then initialize the generated root graph at your platform startup or DI bootstrap point:

```kotlin
StitchInjector.init(StitchSingletonGraph())
```

On Android, this can live in `Application.onCreate()` or another startup entry point such as an initializer. On other platforms, initialize it wherever your app bootstraps global dependencies.

---

## Migrating from Dagger 2 / Hilt

### From Dagger 2

#### Scopes

In Dagger 2, scopes are commonly expressed through custom annotations plus `@Component` or `@Subcomponent` wiring:

```kotlin
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class ActivityScope

@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class FragmentScope

@Singleton
@Component(modules = [AppModule::class])
interface AppComponent

@ActivityScope
@Subcomponent(modules = [HomeActivityModule::class])
interface HomeActivityComponent

@FragmentScope
@Subcomponent(modules = [HomeFragmentModule::class])
interface HomeFragmentComponent
```

In Stitch, the scope annotations remain, but graph dependencies come from `@DependsOn` instead of manual component wiring:

```kotlin
import io.github.harrytmthy.stitch.annotations.DependsOn
import io.github.harrytmthy.stitch.annotations.Scope

@Scope
@Retention(AnnotationRetention.BINARY)
annotation class ActivityScope

@Scope
@DependsOn(ActivityScope::class)
@Retention(AnnotationRetention.BINARY)
annotation class FragmentScope
```

After that, you can remove Dagger components and subcomponents entirely.

#### Providing bindings

For the lowest-effort migration, keep your existing binding layout and only swap Stitch annotations where needed.

If you already use an abstract module with `@Binds`, the shape stays almost the same:

```kotlin
// ========= Dagger 2 =========
import dagger.Binds
import dagger.Module

@Module
abstract class AppModule {

    @Binds
    abstract fun bindLogger(impl: LoggerImpl): Logger
}

// ========= Stitch =========
import io.github.harrytmthy.stitch.annotations.Binds

abstract class AppModule {

    @Binds
    abstract fun bindLogger(impl: LoggerImpl): Logger
}
```

If your Dagger module uses an object layout, that also maps cleanly:

```kotlin
// ========= Dagger 2 =========
import dagger.Binds
import dagger.Module

@Module
object AppModule {

    @Singleton
    @Provides
    fun provideLogger(): LoggerImpl = LoggerImpl()

    interface Binder {

        @Binds
        fun bindLogger(logger: LoggerImpl): Logger
    }
}

// ========= Stitch =========
import io.github.harrytmthy.stitch.annotations.Binds
import io.github.harrytmthy.stitch.annotations.Provides
import io.github.harrytmthy.stitch.annotations.Singleton

object AppModule {

    @Singleton
    @Provides
    fun provideLogger(): LoggerImpl = LoggerImpl()

    interface Binder {

        @Binds
        fun bindLogger(logger: LoggerImpl): Logger
    }
}
```

Or flatten it into a top-level declaration when you want less boilerplate:

```kotlin
import io.github.harrytmthy.stitch.annotations.Binds
import io.github.harrytmthy.stitch.annotations.Provides
import io.github.harrytmthy.stitch.annotations.Singleton

@Singleton
@Provides
@Binds(aliases = [Logger::class])
fun provideLogger(): LoggerImpl = LoggerImpl()
```

The constructor injection shape also stays the same. Keep using `javax.inject.*`, except `javax.inject.Scope`:

```kotlin
// ========= Dagger 2 =========
@ActivityScope
class HomeViewModel @Inject constructor(private val logger: Logger) : ViewModel()

// ========= Stitch =========
@ActivityScope
class HomeViewModel @Inject constructor(private val logger: Logger) : ViewModel()
```

You can also merge constructor injection with alias definition:

```kotlin
import io.github.harrytmthy.stitch.annotations.Binds
import io.github.harrytmthy.stitch.annotations.Singleton

@Singleton
@Binds(aliases = [Logger::class])
class LoggerImpl @Inject constructor() : Logger
```

#### Field injection

Field injection still uses `@Inject`, but component builders are replaced by generated injector access:

```kotlin
// ========= Dagger 2 =========
class HomeActivity : AppCompatActivity() {

    @Inject
    lateinit var viewModel: HomeViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        DaggerHomeActivityComponent.create().inject(this)
    }
}

// ========= Stitch =========
class HomeActivity : AppCompatActivity() {

    @Inject
    lateinit var viewModel: HomeViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        StitchInjector.getSingletonGraph()
            .createInjectorForChildScope("ActivityScope")
            .inject(this)
    }
}
```

Scope names are case-insensitive. If you prefer shorter runtime names, define them with `@Scope(name = "...")`:

```kotlin
@Scope("activity")
@Retention(AnnotationRetention.BINARY)
annotation class ActivityScope
```

#### Qualifiers

Stitch supports both `@Named` and custom marker qualifiers, so most Dagger qualifier definitions can be kept as-is:

```kotlin
import io.github.harrytmthy.stitch.annotations.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Production

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Staging
```

The main difference is with aliases. Unlike Dagger's more flexible binding key model, Stitch keeps qualifier propagation simpler: if an alias is qualified, the concrete binding should use the same qualifier too.

```kotlin
// ========= Dagger 2 =========
@Module
interface HomeModule {

    @Staging
    @Binds
    fun bindStagingHomeService(homeService: StagingHomeService): HomeService

    @Production
    @Binds
    fun bindProductionHomeService(homeService: ProductionHomeService): HomeService
}

@Singleton
class StagingHomeService @Inject constructor() : HomeService

@Singleton
class ProductionHomeService @Inject constructor() : HomeService

@Singleton
class HomeServiceProvider @Inject constructor(
    @Staging private val stagingHomeService: HomeService,
    @Production private val prodHomeService: HomeService,
)

// ========= Stitch =========
interface HomeModule {

    @Staging
    @Binds
    fun bindStagingHomeService(homeService: StagingHomeService): HomeService

    @Production
    @Binds
    fun bindProductionHomeService(homeService: ProductionHomeService): HomeService
}

@Staging
@Singleton
class StagingHomeService @Inject constructor() : HomeService

@Production
@Singleton
class ProductionHomeService @Inject constructor() : HomeService

@Singleton
class HomeServiceProvider @Inject constructor(
    @Staging private val stagingHomeService: HomeService,
    @Production private val prodHomeService: HomeService,
)
```

The easiest Stitch-native version is to chain `@Binds` directly onto constructor injection:

```kotlin
@Staging
@Singleton
@Binds(aliases = [HomeService::class])
class StagingHomeService @Inject constructor() : HomeService

@Production
@Singleton
@Binds(aliases = [HomeService::class])
class ProductionHomeService @Inject constructor() : HomeService
```

### From Hilt

Hilt is still Dagger underneath, but it adds platform conveniences such as predefined components, `@InstallIn`, and `@AndroidEntryPoint`.

#### Scopes

Hilt's built-in scopes map naturally to Stitch custom scopes:

```kotlin
// ========= Hilt =========
@ActivityScoped
class HomePresenter @Inject constructor()

@FragmentScoped
class HomeViewModel @Inject constructor()

// ========= Stitch =========
import io.github.harrytmthy.stitch.annotations.DependsOn
import io.github.harrytmthy.stitch.annotations.Scope

@Scope
@Retention(AnnotationRetention.BINARY)
annotation class Activity

@Scope
@DependsOn(Activity::class)
@Retention(AnnotationRetention.BINARY)
annotation class Fragment

@Activity
class HomePresenter @Inject constructor()

@Fragment
class HomeViewModel @Inject constructor()
```

#### Providing bindings

Remove `@InstallIn`, but keep the existing binding layout as much as possible:

```kotlin
// ========= Hilt =========
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    abstract fun bindApiService(impl: ApiServiceImpl): ApiService

    companion object {
        
        @Provides
        @Singleton
        fun provideRetrofit(): Retrofit = Retrofit.Builder().build()
    }
}

// ========= Stitch =========
import io.github.harrytmthy.stitch.annotations.Binds
import io.github.harrytmthy.stitch.annotations.Provides
import io.github.harrytmthy.stitch.annotations.Singleton

abstract class NetworkModule {

    @Binds
    abstract fun bindApiService(impl: ApiServiceImpl): ApiService

    companion object {
        
        @Provides
        @Singleton
        fun provideRetrofit(): Retrofit = Retrofit.Builder().build()
    }
}
```

#### Field injection

Replace `@AndroidEntryPoint` with an explicit injector call:

```kotlin
// ========= Hilt =========
@AndroidEntryPoint
class HomeActivity : AppCompatActivity() {

    @Inject
    lateinit var viewModel: HomeViewModel
}

// ========= Stitch =========
class HomeActivity : AppCompatActivity() {

    @Inject
    lateinit var viewModel: HomeViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        StitchInjector.getSingletonGraph()
            .createInjectorForChildScope("activity")
            .inject(this)
    }
}
```

#### Initialization

Hilt wires itself automatically. Stitch is explicit, so initialize the generated graph once at app startup or bootstrap time:

```kotlin
StitchInjector.init(StitchSingletonGraph())
```

### Features Not Yet Available

These are common Dagger 2 / Hilt features that are not in Stitch v1.0 yet:

| Feature                | Dagger 2 / Hilt                   | Planned in Stitch |
|------------------------|-----------------------------------|:-----------------:|
| Multibindings          | `@IntoSet`, `@IntoMap`, `@MapKey` |       v1.1        |
| Assisted injection     | `@Assisted`, `@AssistedFactory`   |       v1.1        |
| Optional bindings      | `@BindsOptionalOf`                |       v1.1        |
| Provider wrapping      | `Provider<T>`                     |       v1.1        |
| Reusable scope         | `@Reusable`                       |         -         |
| Multiple scope parents | Multiple component dependencies   |         -         |

---

## Migrating from Koin

### To the Runtime Registration Path

This is the most direct migration for Koin users.

#### Defining modules

Most bindings translate one-to-one:

```kotlin
// ========= Koin =========
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val activityScope = named("activity")

val appModule = module {
    single { LoggerImpl() }.bind<Logger>()
    factory { HomeConfig() }
    scope(activityScope) {
        scoped { HomeViewModel() }
    }
}

startKoin {
    modules(appModule)
}

// ========= Stitch =========
import io.github.harrytmthy.stitch.api.bind
import io.github.harrytmthy.stitch.api.module
import io.github.harrytmthy.stitch.api.scope

val activityScope = scope("activity")

val appModule = module {
    singleton { LoggerImpl() }.bind<Logger>()
    factory { HomeConfig() }
    scoped(activityScope) { HomeViewModel() }
}

Stitch.register(appModule)
```

#### Resolving dependencies

Replace Koin retrieval imports with Stitch's direct APIs:

```kotlin
// ========= Koin =========
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject

val logger: Logger = get()
val lazyLogger: Logger by inject()

// ========= Stitch =========
import com.harrytmthy.stitch.api.Stitch

val logger: Logger = Stitch.get()
val lazyLogger: Logger by Stitch.inject()
```

Inside module definitions, `get()` keeps the same shape:

```kotlin
// ========= Koin =========
val homeModule = module {
    factory { HomeViewModel(logger = get()) }
}

// ========= Stitch =========
val homeModule = module {
    factory { HomeViewModel(logger = get()) }
}
```

#### Scoped bindings

Translate Koin scope creation directly into Stitch scope creation:

```kotlin
// ========= Koin =========
val homeActivityScope = getKoin().createScope<HomeActivity>()
val viewModel = homeActivityScope.get<HomeViewModel>()
homeActivityScope.close()

// ========= Stitch =========
val homeActivityScope = scope("activity").createScope()
val viewModel = homeActivityScope.get<HomeViewModel>()
homeActivityScope.close()
```

#### Parent-child scopes

Koin does not provide the same explicit parent-child scope dependency model. Stitch supports it directly:

```kotlin
val activityScope = scope("activity")
val fragmentScope = scope("fragment").dependsOn(activityScope)

val homeActivityScope = activityScope.createScope()
val homeFragmentScope = homeActivityScope.createChildScope(fragmentScope)

// Resolves from fragment scope first, then falls back to activity scope
val viewModel: HomeViewModel = homeFragmentScope.get()
```

#### Qualifiers

String qualifiers map directly, and enum-based names also work:

```kotlin
// ========= Koin =========
single(named("prod")) { ProdConfig() }
single(named(Environment.Prod)) { ProdConfig() }

// ========= Stitch =========
singleton(qualifier = named("prod")) { ProdConfig() }
singleton(qualifier = named(Environment.Prod)) { ProdConfig() }
```

Typed qualifiers also exist when you want to avoid string duplication:

```kotlin
singleton(qualifier = typed<Production>()) { ProdConfig() }
```

### To the Precompiled Path

If you currently use Koin annotations, this is the closest migration path.

#### Providing bindings

Translate `@Single` and annotation-driven definitions into constructor injection or Stitch-supported providers:

```kotlin
// ========= Koin =========
@Single
class LoggerImpl : Logger

// ========= Stitch =========
@Singleton
class LoggerImpl @Inject constructor() : Logger
```

Or keep a provider layout when that is closer to your existing code:

```kotlin
import io.github.harrytmthy.stitch.annotations.Binds
import io.github.harrytmthy.stitch.annotations.Provides
import io.github.harrytmthy.stitch.annotations.Singleton

object AppModule {

    @Singleton
    @Provides
    fun provideLogger(): LoggerImpl = LoggerImpl()

    interface Binder {

        @Binds
        fun bindLogger(logger: LoggerImpl): Logger
    }
}
```

#### Scopes

Translate Koin annotation scopes into Stitch scope annotations:

```kotlin
// ========= Koin =========
import org.koin.core.annotation.Scope

@Scope(MainActivity::class)
class HomeViewModel @Inject constructor(...)

// ========= Stitch =========
import io.github.harrytmthy.stitch.annotations.Scope

@Scope("MainActivity")
class HomeViewModel @Inject constructor(...)
```

#### Field injection

Replace delegated or direct retrieval with either generated injector access or field injection:

```kotlin
// ========= Koin =========
import org.koin.android.ext.android.get

class HomeActivity : AppCompatActivity() {

    private val logger: Logger = get()
}

// ========= Stitch =========
import io.github.harrytmthy.stitch.api.get

class HomeActivity : AppCompatActivity() {

    private val logger: Logger = StitchInjector.getSingletonGraph().get()
}
```

Or use field injection:

```kotlin
class HomeActivity : AppCompatActivity() {

    @Inject
    lateinit var logger: Logger

    override fun onCreate(savedInstanceState: Bundle?) {
        StitchInjector.getSingletonGraph().inject(this)
    }
}
```

#### Qualifiers

Replace Koin's qualifier annotations with Stitch's supported annotations:

```kotlin
// ========= Koin =========
import org.koin.core.annotation.Named

@Single
@Named("prod")
class ProdConfig : Config

// ========= Stitch =========
import io.github.harrytmthy.stitch.annotations.Binds
import io.github.harrytmthy.stitch.annotations.Named
import io.github.harrytmthy.stitch.annotations.Singleton

@Singleton
@Named("prod")
@Binds(aliases = [Config::class])
class ProdConfig @Inject constructor() : Config
```

---

## Final Notes

If you are moving from **Dagger 2 / Hilt**, start with the precompiled path first.

If you are moving from **Koin**, start with the runtime registration path first.

If your project currently mixes both, Stitch is designed to replace that split gradually.

For v1.0, please keep this boundary in mind:
- Use `StitchInjector` and generated graphs for the precompiled path
- Use `Stitch` for the runtime registration path

Precompiled bindings are not retrievable through `Stitch.get<T>()`.
