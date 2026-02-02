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

package com.harrytmthy.stitch.benchmark

import javax.inject.Inject
import javax.inject.Singleton

// Shallow dependencies (3 simple classes)
@Singleton
class A @Inject constructor()

@Singleton
class B @Inject constructor()

@Singleton
class C @Inject constructor()

// Deep dependency chain (30 levels)
@Singleton
class D1 @Inject constructor()

@Singleton
class D2 @Inject constructor(val d1: D1)

@Singleton
class D3 @Inject constructor(val d2: D2)

@Singleton
class D4 @Inject constructor(val d3: D3)

@Singleton
class D5 @Inject constructor(val d4: D4)

@Singleton
class D6 @Inject constructor(val d5: D5)

@Singleton
class D7 @Inject constructor(val d6: D6)

@Singleton
class D8 @Inject constructor(val d7: D7)

@Singleton
class D9 @Inject constructor(val d8: D8)

@Singleton
class D10 @Inject constructor(val d9: D9)

@Singleton
class D11 @Inject constructor(val d10: D10)

@Singleton
class D12 @Inject constructor(val d11: D11)

@Singleton
class D13 @Inject constructor(val d12: D12)

@Singleton
class D14 @Inject constructor(val d13: D13)

@Singleton
class D15 @Inject constructor(val d14: D14)

@Singleton
class D16 @Inject constructor(val d15: D15)

@Singleton
class D17 @Inject constructor(val d16: D16)

@Singleton
class D18 @Inject constructor(val d17: D17)

@Singleton
class D19 @Inject constructor(val d18: D18)

@Singleton
class D20 @Inject constructor(val d19: D19)

@Singleton
class D21 @Inject constructor(val d20: D20)

@Singleton
class D22 @Inject constructor(val d21: D21)

@Singleton
class D23 @Inject constructor(val d22: D22)

@Singleton
class D24 @Inject constructor(val d23: D23)

@Singleton
class D25 @Inject constructor(val d24: D24)

@Singleton
class D26 @Inject constructor(val d25: D25)

@Singleton
class D27 @Inject constructor(val d26: D26)

@Singleton
class D28 @Inject constructor(val d27: D27)

@Singleton
class D29 @Inject constructor(val d28: D28)

@Singleton
class D30 @Inject constructor(val d29: D29)

// ========== Factory (Cold Path) Dependencies ==========
// These classes are NOT annotated with @Singleton, so they create new instances each time

// Shallow factory dependencies (3 simple classes)
class E @Inject constructor()

class F @Inject constructor()

class G @Inject constructor()

// Deep factory dependency chain (30 levels)
class H1 @Inject constructor()

class H2 @Inject constructor(val h1: H1)

class H3 @Inject constructor(val h2: H2)

class H4 @Inject constructor(val h3: H3)

class H5 @Inject constructor(val h4: H4)

class H6 @Inject constructor(val h5: H5)

class H7 @Inject constructor(val h6: H6)

class H8 @Inject constructor(val h7: H7)

class H9 @Inject constructor(val h8: H8)

class H10 @Inject constructor(val h9: H9)

class H11 @Inject constructor(val h10: H10)

class H12 @Inject constructor(val h11: H11)

class H13 @Inject constructor(val h12: H12)

class H14 @Inject constructor(val h13: H13)

class H15 @Inject constructor(val h14: H14)

class H16 @Inject constructor(val h15: H15)

class H17 @Inject constructor(val h16: H16)

class H18 @Inject constructor(val h17: H17)

class H19 @Inject constructor(val h18: H18)

class H20 @Inject constructor(val h19: H19)

class H21 @Inject constructor(val h20: H20)

class H22 @Inject constructor(val h21: H21)

class H23 @Inject constructor(val h22: H22)

class H24 @Inject constructor(val h23: H23)

class H25 @Inject constructor(val h24: H24)

class H26 @Inject constructor(val h25: H25)

class H27 @Inject constructor(val h26: H26)

class H28 @Inject constructor(val h27: H27)

class H29 @Inject constructor(val h28: H28)

class H30 @Inject constructor(val h29: H29)

/**
 * Shallow target with 3 @Inject fields.
 * Used for benchmarking member injection on simple objects.
 */
class ShallowTarget {
    @Inject lateinit var a: A
    @Inject lateinit var b: B
    @Inject lateinit var c: C
}

/**
 * Deep target with 30 @Inject fields.
 * Used for benchmarking member injection on complex objects with deep dependency chains.
 */
class DeepTarget {
    @Inject lateinit var d1: D1
    @Inject lateinit var d2: D2
    @Inject lateinit var d3: D3
    @Inject lateinit var d4: D4
    @Inject lateinit var d5: D5
    @Inject lateinit var d6: D6
    @Inject lateinit var d7: D7
    @Inject lateinit var d8: D8
    @Inject lateinit var d9: D9
    @Inject lateinit var d10: D10
    @Inject lateinit var d11: D11
    @Inject lateinit var d12: D12
    @Inject lateinit var d13: D13
    @Inject lateinit var d14: D14
    @Inject lateinit var d15: D15
    @Inject lateinit var d16: D16
    @Inject lateinit var d17: D17
    @Inject lateinit var d18: D18
    @Inject lateinit var d19: D19
    @Inject lateinit var d20: D20
    @Inject lateinit var d21: D21
    @Inject lateinit var d22: D22
    @Inject lateinit var d23: D23
    @Inject lateinit var d24: D24
    @Inject lateinit var d25: D25
    @Inject lateinit var d26: D26
    @Inject lateinit var d27: D27
    @Inject lateinit var d28: D28
    @Inject lateinit var d29: D29
    @Inject lateinit var d30: D30
}

/**
 * Shallow target with 3 @Inject fields (factory dependencies, cold path).
 * Used for benchmarking member injection on simple objects with new instances each time.
 */
class ShallowTargetCold {
    @Inject lateinit var e: E
    @Inject lateinit var f: F
    @Inject lateinit var g: G
}

/**
 * Deep target with 30 @Inject fields (factory dependencies, cold path).
 * Used for benchmarking member injection on complex objects with deep dependency chains
 * where new instances are created each time.
 */
class DeepTargetCold {
    @Inject lateinit var h1: H1
    @Inject lateinit var h2: H2
    @Inject lateinit var h3: H3
    @Inject lateinit var h4: H4
    @Inject lateinit var h5: H5
    @Inject lateinit var h6: H6
    @Inject lateinit var h7: H7
    @Inject lateinit var h8: H8
    @Inject lateinit var h9: H9
    @Inject lateinit var h10: H10
    @Inject lateinit var h11: H11
    @Inject lateinit var h12: H12
    @Inject lateinit var h13: H13
    @Inject lateinit var h14: H14
    @Inject lateinit var h15: H15
    @Inject lateinit var h16: H16
    @Inject lateinit var h17: H17
    @Inject lateinit var h18: H18
    @Inject lateinit var h19: H19
    @Inject lateinit var h20: H20
    @Inject lateinit var h21: H21
    @Inject lateinit var h22: H22
    @Inject lateinit var h23: H23
    @Inject lateinit var h24: H24
    @Inject lateinit var h25: H25
    @Inject lateinit var h26: H26
    @Inject lateinit var h27: H27
    @Inject lateinit var h28: H28
    @Inject lateinit var h29: H29
    @Inject lateinit var h30: H30
}