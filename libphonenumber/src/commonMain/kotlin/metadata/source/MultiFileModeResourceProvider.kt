/*
 * Copyright (C) 2022 The Libphonenumber Authors
 * Copyright (C) 2022 Michael Rozumyanskiy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package libphonenumber.metadata.source

import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.readResourceBytes

/**
 * [PhoneMetadataResourceProvider] implementation which appends key as a suffix to the
 * predefined metadata file name base.
 */
class MultiFileModeResourceProvider(
    phoneMetadataFileNameBase: String,
) : PhoneMetadataResourceProvider {
    private val phoneMetadataFileNamePrefix: String = phoneMetadataFileNameBase + "_"

    @OptIn(InternalResourceApi::class)
    override fun getFor(key: Any): String {
        val keyAsString = key.toString()
        require(ALPHANUMERIC.matchEntire(keyAsString) != null) { "Invalid key: $keyAsString" }
        val path = phoneMetadataFileNamePrefix + keyAsString
        return path
    }

    companion object {
        // https://youtrack.jetbrains.com/issue/KT-58678/Native-Regex-inconsistency-with-JVM-Native-Regex
        private val ALPHANUMERIC = Regex("^([a-zA-Z0-9]+)$")
//        private val ALPHANUMERIC = Regex("^[\\p{L}\\p{N}]+$")
    }
}
