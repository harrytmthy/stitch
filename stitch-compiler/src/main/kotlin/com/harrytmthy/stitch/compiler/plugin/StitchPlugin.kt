/*
 * Copyright 2026 Harry Timothy Tumalewa
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

package com.harrytmthy.stitch.compiler.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.security.MessageDigest

class StitchPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.withPlugin("com.google.devtools.ksp") {
            // Configure by extension name to avoid KspExtension class reference
            val kspExt = project.extensions.findByName("ksp") ?: return@withPlugin

            fun kspArg(key: String, value: String) {
                val method = kspExt.javaClass.methods.firstOrNull { m ->
                    m.name == "arg" &&
                        m.parameterTypes.size == 2 &&
                        m.parameterTypes[0] == String::class.java &&
                        m.parameterTypes[1] == String::class.java
                } ?: error("Unable to find KSP 'arg(String, String)' method. KSP plugin API changed?")
                method.invoke(kspExt, key, value)
            }

            // Try to read existing arguments map, if present, to avoid overriding manual config
            val existingArgs: Map<String, String> =
                runCatching {
                    val getter = kspExt.javaClass.methods.firstOrNull { it.name == "getArguments" && it.parameterTypes.isEmpty() }
                    @Suppress("UNCHECKED_CAST")
                    (getter?.invoke(kspExt) as? Map<String, String>) ?: emptyMap()
                }.getOrDefault(emptyMap())

            if (!existingArgs.containsKey("stitch.modulePath")) {
                kspArg("stitch.modulePath", project.path)
            }
            if (!existingArgs.containsKey("stitch.moduleName")) {
                kspArg("stitch.moduleName", project.path.toPascalModuleName())
            }
            if (!existingArgs.containsKey("stitch.moduleKey")) {
                val length = existingArgs["stitch.moduleKeyLength"]?.toIntOrNull() ?: 8
                kspArg("stitch.moduleKey", project.path.toStableModuleKey(length))
            }
        }
    }

    private fun String.toPascalModuleName(): String =
        split(':')
            .filter { it.isNotBlank() }
            .joinToString("") { part ->
                part.split('-', '.', '_')
                    .filter { it.isNotBlank() }
                    .joinToString("") { seg ->
                        seg.replaceFirstChar { if (it.isLowerCase()) it.titlecaseChar() else it }
                    }
            }
            .ifBlank { error("Unable to transform '$this' to pascal case") } // Should not happen

    private fun String.toStableModuleKey(length: Int): String {
        require(length >= 8 && length % 2 == 0) { "length must be even and >= 8" }
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        val bytesNeeded = length / 2
        val key = digest.take(bytesNeeded).joinToString("") { "%02X".format(it) }

        // Package-safe prefix (package segments can't start with digits)
        return "s$key"
    }
}
