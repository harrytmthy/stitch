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

package com.harrytmthy.stitch.api

import com.harrytmthy.stitch.exception.MissingBindingException
import com.harrytmthy.stitch.internal.Node
import com.harrytmthy.stitch.internal.Registry
import com.harrytmthy.stitch.internal.Signature

object Stitch {

    private val component by lazy {
        Component(
            nodeLookup = ::lookupNode,
            singletons = Registry.singletons,
            scoped = Registry.scoped,
        )
    }

    fun register(vararg modules: Module) {
        modules.forEach { module ->
            module.register()
            val signatures = module.binder.getStagedEagerDefinitions()
            if (signatures.isNotEmpty()) {
                warmUp(signatures)
            }
        }
    }

    private fun warmUp(signatures: List<Signature>) {
        for (signature in signatures) {
            component.get(signature.type, signature.qualifier)
        }
    }

    fun unregister() {
        Registry.clear()
        Named.clear()
        ScopeRef.clear()
        component.clear()
    }

    inline fun <reified T : Any> get(qualifier: Qualifier? = null, scope: Scope? = null): T =
        get(T::class.java, qualifier, scope)

    fun <T : Any> get(type: Class<T>, qualifier: Qualifier? = null, scope: Scope? = null): T =
        component.get(type, qualifier, scope)

    internal fun lookupNode(type: Class<*>, qualifier: Qualifier?, scopeRef: ScopeRef?): Node {
        Registry.scopedDefinitions[type]?.get(qualifier)?.get(scopeRef)?.let { return it }
        val inner = Registry.definitions[type] ?: throw MissingBindingException.missingType(type)
        return inner.getOrElse(qualifier) {
            throw MissingBindingException.missingQualifier(type, qualifier, inner.keys)
        }
    }
}
