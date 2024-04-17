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
package metadata.source

import kotlinx.coroutines.CoroutineScope
import libphonenumber.Phonemetadata.PhoneMetadata

/**
 * A source of phone metadata split by geographical regions.
 */
interface RegionMetadataSource {
    /**
     * Returns phone metadata for provided geographical region.
     *
     *
     * The `regionCode` must be different from [ ][GeoEntityUtility.REGION_CODE_FOR_NON_GEO_ENTITIES], which has a special meaning and is used to
     * mark non-geographical regions (see [NonGeographicalEntityMetadataSource] for more
     * information).
     *
     * @return the phone metadata for provided `regionCode`, or null if there is none.
     * @throws IllegalArgumentException if provided `regionCode` is [ ][GeoEntityUtility.REGION_CODE_FOR_NON_GEO_ENTITIES]
     */
    suspend fun getMetadataForRegion(regionCode: String?, coroutineScope: CoroutineScope): PhoneMetadata?
}
