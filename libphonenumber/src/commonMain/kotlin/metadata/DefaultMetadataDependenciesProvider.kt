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
package libphonenumber.metadata

import kotlinx.coroutines.CoroutineScope
import metadata.init.MetadataParser.Companion.newLenientParser
import libphonenumber.metadata.source.*
import metadata.source.FormattingMetadataSource
import metadata.source.MetadataSource
import metadata.source.RegionMetadataSource
import kotlin.jvm.JvmOverloads

/**
 * Provides metadata init and source dependencies when metadata is stored in multi-file mode and
 * loaded as a classpath resource.
 */
class DefaultMetadataDependenciesProvider @JvmOverloads constructor(coroutineScope: CoroutineScope) {
    val metadataParser = newLenientParser()
    val phoneNumberMetadataFileNameProvider: PhoneMetadataResourceProvider = MultiFileModeResourceProvider(
        "PhoneNumberMetadataProto"
    )
    val phoneNumberMetadataSource: MetadataSource
    val shortNumberMetadataFileNameProvider: PhoneMetadataResourceProvider = MultiFileModeResourceProvider(
        "ShortNumberMetadataProto"
    )
    val shortNumberMetadataSource: RegionMetadataSource
    val alternateFormatsMetadataFileNameProvider: PhoneMetadataResourceProvider = MultiFileModeResourceProvider(
        "PhoneNumberAlternateFormatsProto"
    )
    val alternateFormatsMetadataSource: FormattingMetadataSource

    init {
        phoneNumberMetadataSource = MetadataSourceImpl(
            phoneNumberMetadataFileNameProvider,
            metadataParser,
            coroutineScope
        )
        shortNumberMetadataSource = RegionMetadataSourceImpl(
            shortNumberMetadataFileNameProvider,
            metadataParser,
            coroutineScope
        )
        alternateFormatsMetadataSource = FormattingMetadataSourceImpl(
            alternateFormatsMetadataFileNameProvider,
            metadataParser,
            coroutineScope
        )
    }

    val carrierDataDirectory: String
        get() = "io/michaelrocks/libphonenumber/android/carrier/data/"
    val geocodingDataDirectory: String
        get() = "io/michaelrocks/libphonenumber/android/geocoding/data/"
}
