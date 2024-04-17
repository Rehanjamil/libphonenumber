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

import kotlinx.coroutines.CoroutineScope
import libphonenumber.Phonemetadata.PhoneMetadata
import metadata.source.MapBackedMetadataContainer.Companion.byCountryCallingCode
import metadata.init.MetadataParser
import metadata.source.BlockingMetadataBootstrappingGuard
import metadata.source.FormattingMetadataSource
import metadata.source.MapBackedMetadataContainer

/**
 * Implementation of [FormattingMetadataSource] guarded by [MetadataBootstrappingGuard]
 *
 *
 * By default, a [BlockingMetadataBootstrappingGuard] will be used, but any custom
 * implementation can be injected.
 */
class FormattingMetadataSourceImpl(
    private val phoneMetadataResourceProvider: PhoneMetadataResourceProvider,
    private val bootstrappingGuard: MetadataBootstrappingGuard<MapBackedMetadataContainer<Int>>,
    private val coroutineScope: CoroutineScope
) : FormattingMetadataSource {
    constructor(
        phoneMetadataResourceProvider: PhoneMetadataResourceProvider,
        metadataParser: MetadataParser?,
        coroutineScope: CoroutineScope
    ) : this(
        phoneMetadataResourceProvider,
        BlockingMetadataBootstrappingGuard<MapBackedMetadataContainer<Int>>(
            metadataParser!!, byCountryCallingCode(),
            coroutineScope = coroutineScope
        ),
        coroutineScope
    )

    override suspend fun getFormattingMetadataForCountryCallingCode(countryCallingCode: Int): PhoneMetadata? {
        return bootstrappingGuard
            .getOrBootstrap(phoneMetadataResourceProvider.getFor(countryCallingCode))
            ?.getMetadataBy(countryCallingCode)
    }
}
