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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import libphonenumber.Phonemetadata.PhoneMetadata
import libphonenumber.internal.GeoEntityUtility.isGeoEntity
import metadata.init.MetadataParser
import metadata.source.BlockingMetadataBootstrappingGuard
import metadata.source.MapBackedMetadataContainer
import metadata.source.RegionMetadataSource

/**
 * Implementation of [RegionMetadataSource] guarded by [MetadataBootstrappingGuard]
 *
 *
 * By default, a [BlockingMetadataBootstrappingGuard] will be used, but any custom
 * implementation can be injected.
 */
class RegionMetadataSourceImpl(
    private val phoneMetadataResourceProvider: PhoneMetadataResourceProvider,
    private val bootstrappingGuard: MetadataBootstrappingGuard<MapBackedMetadataContainer<String>>,
    val coroutineScope: CoroutineScope
) : RegionMetadataSource {
    constructor(
        phoneMetadataResourceProvider: PhoneMetadataResourceProvider,
        metadataParser: MetadataParser,
        coroutineScope: CoroutineScope
    ) : this(
        phoneMetadataResourceProvider,
        BlockingMetadataBootstrappingGuard<MapBackedMetadataContainer<String>>(
            metadataParser,
            MapBackedMetadataContainer.byRegionCode(),
            coroutineScope
        ),
        coroutineScope
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getMetadataForRegion(
        regionCode: String?,
        coroutineScope: CoroutineScope
    ): PhoneMetadata? {
        require(isGeoEntity(regionCode!!)) { "$regionCode region code is a non-geo entity" }
        val metadata =  bootstrappingGuard
            .getOrBootstrap(phoneMetadataResourceProvider.getFor(regionCode))
            ?.getMetadataBy(regionCode)

        return metadata
    }
}
