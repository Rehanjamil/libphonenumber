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

import io.michaelrocks.libphonenumber.kotlin.Phonemetadata
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import libphonenumber.metadata.source.MetadataBootstrappingGuard
import libphonenumber_kotlin.libphonenumber.generated.resources.Res.readBytes
import metadata.init.MetadataParser
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * A blocking implementation of [MetadataBootstrappingGuard]. Can be used for both single-file
 * (bulk) and multi-file metadata
 *
 * @param <T> needs to extend [MetadataContainer]
</T> */
internal class BlockingMetadataBootstrappingGuard<T : MetadataContainer>(
    private val metadataParser: MetadataParser,
    private val metadataContainer: T,
    private val coroutineScope: CoroutineScope
) : MetadataBootstrappingGuard<T> {
    private val loadedFiles: MutableMap<String, Collection<Phonemetadata.PhoneMetadata>?> = mutableMapOf()


    override suspend fun getOrBootstrap(phoneMetadataResource: String): T? {
        phoneMetadataResource ?: return null

        loadedFiles.getOrPut(phoneMetadataResource) {
            try {
                val phoneMetadata = bootstrapMetadata(phoneMetadataResource)
                println("metaDataFound : $phoneMetadata")
                loadedFiles[phoneMetadataResource] = phoneMetadata
                phoneMetadata
            } catch (e: Exception) {
                null
            }

        }
//        if (!loadedFiles.contains(phoneMetadataResource)) {
//            coroutineScope.launch {
//                bootstrapMetadata(phoneMetadataResource)
//            }.invokeOnCompletion {
//                print(it?.message)
//            }
//        }
        return metadataContainer
    }


    private suspend fun bootstrapMetadata(phoneMetadataResource: String): Collection<Phonemetadata.PhoneMetadata> {
        // Additional check is needed because multiple threads could pass the first check when calling
        // getOrBootstrap() at the same time for unloaded metadata file
        val phoneMetadata = read(phoneMetadataResource)
        for (data in phoneMetadata) {
            metadataContainer.accept(data)
        }
        return phoneMetadata
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun read(phoneMetadataResource: String): Collection<Phonemetadata.PhoneMetadata> {
        return try {
            val metadataStream = readBytes(phoneMetadataResource)
            metadataParser.parse(metadataStream)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("Failed to read file $phoneMetadataResource", e)
        } catch (e: IllegalStateException) {
            throw IllegalStateException("Failed to read file $phoneMetadataResource", e)
        }
    }

}

