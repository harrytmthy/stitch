/*
 * Copyright 2025 Harry Timothy Tumalewa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.harrytmthy.stitch

interface Repo
class RepoImpl : Repo
class FetchUseCase(val repo: Repo)

interface Auditable

class DualRepo : Repo, Auditable

class Logger
class Dao(val logger: Logger)
class LoadUseCase(val dao: Dao)

class NeedsMissing(val repo: Repo) // used to trigger MissingBindingException

class A(val b: B)
class B(val a: A) // used to trigger CycleException

class UsesLazyFactory(val barLazy: Lazy<Bar>)
class Bar

interface LifecycleTracker
class ViewModelLifecycleTracker : LifecycleTracker
class ActivityLifecycleTracker : LifecycleTracker
class HomeFeatureConfig
class HomeViewModel(tracker: ViewModelLifecycleTracker, config: HomeFeatureConfig)
