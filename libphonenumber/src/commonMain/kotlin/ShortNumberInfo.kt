/*
 * Copyright (C) 2013 The Libphonenumber Authors
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

import io.michaelrocks.libphonenumber.kotlin.Phonemetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import libphonenumber.CountryCodeToRegionCodeMap.countryCodeToRegionCodeMap
import libphonenumber.Phonenumber.PhoneNumber
import libphonenumber.internal.MatcherApi
import metadata.source.RegionMetadataSource


/**
 * Methods for getting information about short phone numbers, such as short codes and emergency
 * numbers. Note that most commercial short numbers are not handled here, but by the
 * [PhoneNumberUtil].
 *
 * @author Shaopeng Jia
 * @author David Yonge-Mallo
 */
class ShortNumberInfo internal constructor(// MatcherApi supports the basic matching method for checking if a given national number matches
    // a national number pattern defined in the given {@code PhoneNumberDesc}.
    private val matcherApi: MatcherApi,
    private val shortNumberMetadataSource: RegionMetadataSource,
    val coroutineScope: CoroutineScope
) {
    /** Cost categories of short numbers.  */
    enum class ShortNumberCost {
        TOLL_FREE,
        STANDARD_RATE,
        PREMIUM_RATE,
        UNKNOWN_COST
    }

    // A mapping from a country calling code to the region codes which denote the region represented
    // by that country calling code. In the case of multiple regions sharing a calling code, such as
    // the NANPA regions, the one indicated with "isMainCountryForCode" in the metadata should be
    // first.
    private val countryCallingCodeToRegionCodeMap: Map<Int, List<String>>

    // @VisibleForTesting
    init {
        // TODO: Create ShortNumberInfo for a given map
        countryCallingCodeToRegionCodeMap = countryCodeToRegionCodeMap
    }

    /**
     * Returns a list with the region codes that match the specific country calling code. For
     * non-geographical country calling codes, the region code 001 is returned. Also, in the case
     * of no region code being found, an empty list is returned.
     */
    private fun getRegionCodesForCountryCode(countryCallingCode: Int): List<String?> {
        val regionCodes = countryCallingCodeToRegionCodeMap[countryCallingCode]
        return regionCodes ?: ArrayList(0)
    }

    /**
     * Helper method to check that the country calling code of the number matches the region it's
     * being dialed from.
     */
    private fun regionDialingFromMatchesNumber(
        number: PhoneNumber,
        regionDialingFrom: String?
    ): Boolean {
        val regionCodes = getRegionCodesForCountryCode(number.countryCode)
        return regionCodes.contains(regionDialingFrom)
    }

    /**
     * A thin wrapper around `shortNumberMetadataSource` which catches [ ] for invalid region code and instead returns `null`
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun getShortNumberMetadataForRegion(regionCode: String?): Phonemetadata.PhoneMetadata? {
        return if (regionCode == null) {
            null
        } else try {
            coroutineScope.async {
                shortNumberMetadataSource.getMetadataForRegion(regionCode, this)
            }.getCompleted()
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Check whether a short number is a possible number when dialed from the given region. This
     * provides a more lenient check than [.isValidShortNumberForRegion].
     *
     * @param number the short number to check
     * @param regionDialingFrom the region from which the number is dialed
     * @return whether the number is a possible short number
     */
    fun isPossibleShortNumberForRegion(number: PhoneNumber, regionDialingFrom: String?): Boolean {
        if (!regionDialingFromMatchesNumber(number, regionDialingFrom)) {
            return false
        }
        val phoneMetadata = getShortNumberMetadataForRegion(regionDialingFrom) ?: return false
        val numberLength = getNationalSignificantNumber(number).length
        return phoneMetadata.generalDesc!!.possibleLengthList.contains(numberLength)
    }

    /**
     * Check whether a short number is a possible number. If a country calling code is shared by
     * multiple regions, this returns true if it's possible in any of them. This provides a more
     * lenient check than [.isValidShortNumber]. See [ ][.isPossibleShortNumberForRegion] for details.
     *
     * @param number the short number to check
     * @return whether the number is a possible short number
     */
    fun isPossibleShortNumber(number: PhoneNumber): Boolean {
        val regionCodes = getRegionCodesForCountryCode(number.countryCode)
        val shortNumberLength = getNationalSignificantNumber(number).length
        for (region in regionCodes) {
            val phoneMetadata = getShortNumberMetadataForRegion(region) ?: continue
            if (phoneMetadata.generalDesc!!.possibleLengthList.contains(shortNumberLength)) {
                return true
            }
        }
        return false
    }

    /**
     * Tests whether a short number matches a valid pattern in a region. Note that this doesn't verify
     * the number is actually in use, which is impossible to tell by just looking at the number
     * itself.
     *
     * @param number the short number for which we want to test the validity
     * @param regionDialingFrom the region from which the number is dialed
     * @return whether the short number matches a valid pattern
     */
    fun isValidShortNumberForRegion(number: PhoneNumber, regionDialingFrom: String?): Boolean {
        if (!regionDialingFromMatchesNumber(number, regionDialingFrom)) {
            return false
        }
        val phoneMetadata = getShortNumberMetadataForRegion(regionDialingFrom) ?: return false
        val shortNumber = getNationalSignificantNumber(number)
        val generalDesc = phoneMetadata.generalDesc
        if (!matchesPossibleNumberAndNationalNumber(shortNumber, generalDesc)) {
            return false
        }
        val shortNumberDesc = phoneMetadata.shortCode
        return matchesPossibleNumberAndNationalNumber(shortNumber, shortNumberDesc)
    }

    /**
     * Tests whether a short number matches a valid pattern. If a country calling code is shared by
     * multiple regions, this returns true if it's valid in any of them. Note that this doesn't verify
     * the number is actually in use, which is impossible to tell by just looking at the number
     * itself. See [.isValidShortNumberForRegion] for details.
     *
     * @param number the short number for which we want to test the validity
     * @return whether the short number matches a valid pattern
     */
    fun isValidShortNumber(number: PhoneNumber): Boolean {
        val regionCodes = getRegionCodesForCountryCode(number.countryCode)
        val regionCode = getRegionCodeForShortNumberFromRegionList(number, regionCodes)
        return if (regionCodes.size > 1 && regionCode != null) {
            // If a matching region had been found for the phone number from among two or more regions,
            // then we have already implicitly verified its validity for that region.
            true
        } else isValidShortNumberForRegion(number, regionCode)
    }

    /**
     * Gets the expected cost category of a short number when dialed from a region (however, nothing
     * is implied about its validity). If it is important that the number is valid, then its validity
     * must first be checked using [.isValidShortNumberForRegion]. Note that emergency numbers
     * are always considered toll-free. Example usage:
     * <pre>`// The region for which the number was parsed and the region we subsequently check against
     * // need not be the same. Here we parse the number in the US and check it for Canada.
     * PhoneNumber number = phoneUtil.parse("110", "US");
     * ...
     * String regionCode = "CA";
     * ShortNumberInfo shortInfo = ShortNumberInfo.getInstance();
     * if (shortInfo.isValidShortNumberForRegion(shortNumber, regionCode)) {
     * ShortNumberCost cost = shortInfo.getExpectedCostForRegion(number, regionCode);
     * // Do something with the cost information here.
     * }`</pre>
     *
     * @param number the short number for which we want to know the expected cost category
     * @param regionDialingFrom the region from which the number is dialed
     * @return the expected cost category for that region of the short number. Returns UNKNOWN_COST if
     * the number does not match a cost category. Note that an invalid number may match any cost
     * category.
     */
    fun getExpectedCostForRegion(number: PhoneNumber, regionDialingFrom: String?): ShortNumberCost {
        if (!regionDialingFromMatchesNumber(number, regionDialingFrom)) {
            return ShortNumberCost.UNKNOWN_COST
        }
        // Note that regionDialingFrom may be null, in which case phoneMetadata will also be null.
        val phoneMetadata = getShortNumberMetadataForRegion(regionDialingFrom) ?: return ShortNumberCost.UNKNOWN_COST
        val shortNumber = getNationalSignificantNumber(number)

        // The possible lengths are not present for a particular sub-type if they match the general
        // description; for this reason, we check the possible lengths against the general description
        // first to allow an early exit if possible.
        if (!phoneMetadata.generalDesc!!.possibleLengthList.contains(shortNumber.length)) {
            return ShortNumberCost.UNKNOWN_COST
        }

        // The cost categories are tested in order of decreasing expense, since if for some reason the
        // patterns overlap the most expensive matching cost category should be returned.
        if (matchesPossibleNumberAndNationalNumber(shortNumber, phoneMetadata.premiumRate)) {
            return ShortNumberCost.PREMIUM_RATE
        }
        if (matchesPossibleNumberAndNationalNumber(shortNumber, phoneMetadata.standardRate)) {
            return ShortNumberCost.STANDARD_RATE
        }
        if (matchesPossibleNumberAndNationalNumber(shortNumber, phoneMetadata.tollFree)) {
            return ShortNumberCost.TOLL_FREE
        }
        return if (isEmergencyNumber(shortNumber, regionDialingFrom)) {
            // Emergency numbers are implicitly toll-free.
            ShortNumberCost.TOLL_FREE
        } else ShortNumberCost.UNKNOWN_COST
    }

    /**
     * Gets the expected cost category of a short number (however, nothing is implied about its
     * validity). If the country calling code is unique to a region, this method behaves exactly the
     * same as [.getExpectedCostForRegion]. However, if the country
     * calling code is shared by multiple regions, then it returns the highest cost in the sequence
     * PREMIUM_RATE, UNKNOWN_COST, STANDARD_RATE, TOLL_FREE. The reason for the position of
     * UNKNOWN_COST in this order is that if a number is UNKNOWN_COST in one region but STANDARD_RATE
     * or TOLL_FREE in another, its expected cost cannot be estimated as one of the latter since it
     * might be a PREMIUM_RATE number.
     *
     *
     * For example, if a number is STANDARD_RATE in the US, but TOLL_FREE in Canada, the expected
     * cost returned by this method will be STANDARD_RATE, since the NANPA countries share the same
     * country calling code.
     *
     *
     * Note: If the region from which the number is dialed is known, it is highly preferable to call
     * [.getExpectedCostForRegion] instead.
     *
     * @param number the short number for which we want to know the expected cost category
     * @return the highest expected cost category of the short number in the region(s) with the given
     * country calling code
     */
    fun getExpectedCost(number: PhoneNumber): ShortNumberCost {
        val regionCodes = getRegionCodesForCountryCode(number.countryCode)
        if (regionCodes.size == 0) {
            return ShortNumberCost.UNKNOWN_COST
        }
        if (regionCodes.size == 1) {
            return getExpectedCostForRegion(number, regionCodes[0])
        }
        var cost = ShortNumberCost.TOLL_FREE
        for (regionCode in regionCodes) {
            val costForRegion = getExpectedCostForRegion(number, regionCode)
            when (costForRegion) {
                ShortNumberCost.PREMIUM_RATE -> return ShortNumberCost.PREMIUM_RATE
                ShortNumberCost.UNKNOWN_COST -> cost = ShortNumberCost.UNKNOWN_COST
                ShortNumberCost.STANDARD_RATE -> if (cost != ShortNumberCost.UNKNOWN_COST) {
                    cost = ShortNumberCost.STANDARD_RATE
                }

                ShortNumberCost.TOLL_FREE -> {}
                else -> println("Unrecognised cost for region: $costForRegion")
            }
        }
        return cost
    }

    // Helper method to get the region code for a given phone number, from a list of possible region
    // codes. If the list contains more than one region, the first region for which the number is
    // valid is returned.
    private fun getRegionCodeForShortNumberFromRegionList(
        number: PhoneNumber,
        regionCodes: List<String?>
    ): String? {
        if (regionCodes.size == 0) {
            return null
        } else if (regionCodes.size == 1) {
            return regionCodes[0]
        }
        val nationalNumber = getNationalSignificantNumber(number)
        for (regionCode in regionCodes) {
            val phoneMetadata = getShortNumberMetadataForRegion(regionCode)
            if (phoneMetadata != null
                && matchesPossibleNumberAndNationalNumber(nationalNumber, phoneMetadata.shortCode)
            ) {
                // The number is valid for this region.
                return regionCode
            }
        }
        return null
    }

    /**
     * Gets a valid short number for the specified region.
     *
     * @param regionCode the region for which an example short number is needed
     * @return a valid short number for the specified region. Returns an empty string when the
     * metadata does not contain such information.
     */
    // @VisibleForTesting
    fun getExampleShortNumber(regionCode: String?): String {
        val phoneMetadata = getShortNumberMetadataForRegion(regionCode) ?: return ""
        val desc = phoneMetadata.shortCode
        return if (desc!!.hasExampleNumber()) {
            desc.exampleNumber
        } else ""
    }

    /**
     * Gets a valid short number for the specified cost category.
     *
     * @param regionCode the region for which an example short number is needed
     * @param cost the cost category of number that is needed
     * @return a valid short number for the specified region and cost category. Returns an empty
     * string when the metadata does not contain such information, or the cost is UNKNOWN_COST.
     */
    // @VisibleForTesting
    fun getExampleShortNumberForCost(regionCode: String?, cost: ShortNumberCost?): String {
        val phoneMetadata = getShortNumberMetadataForRegion(regionCode) ?: return ""
        var desc: Phonemetadata.PhoneNumberDesc? = null
        when (cost) {
            ShortNumberCost.TOLL_FREE -> desc = phoneMetadata.tollFree
            ShortNumberCost.STANDARD_RATE -> desc = phoneMetadata.standardRate
            ShortNumberCost.PREMIUM_RATE -> desc = phoneMetadata.premiumRate
            else -> {}
        }
        return if (desc != null && desc.hasExampleNumber()) {
            desc.exampleNumber
        } else ""
    }

    /**
     * Returns true if the given number, exactly as dialed, might be used to connect to an emergency
     * service in the given region.
     *
     *
     * This method accepts a string, rather than a PhoneNumber, because it needs to distinguish
     * cases such as "+1 911" and "911", where the former may not connect to an emergency service in
     * all cases but the latter would. This method takes into account cases where the number might
     * contain formatting, or might have additional digits appended (when it is okay to do that in
     * the specified region).
     *
     * @param number the phone number to test
     * @param regionCode the region where the phone number is being dialed
     * @return whether the number might be used to connect to an emergency service in the given region
     */
    fun connectsToEmergencyNumber(number: String, regionCode: String?): Boolean {
        return matchesEmergencyNumberHelper(number, regionCode, true /* allows prefix match */)
    }

    /**
     * Returns true if the given number exactly matches an emergency service number in the given
     * region.
     *
     *
     * This method takes into account cases where the number might contain formatting, but doesn't
     * allow additional digits to be appended. Note that `isEmergencyNumber(number, region)`
     * implies `connectsToEmergencyNumber(number, region)`.
     *
     * @param number the phone number to test
     * @param regionCode the region where the phone number is being dialed
     * @return whether the number exactly matches an emergency services number in the given region
     */
    fun isEmergencyNumber(number: CharSequence, regionCode: String?): Boolean {
        return matchesEmergencyNumberHelper(number, regionCode, false /* doesn't allow prefix match */)
    }

    private fun matchesEmergencyNumberHelper(
        number: CharSequence, regionCode: String?,
        allowPrefixMatch: Boolean
    ): Boolean {
        val possibleNumber = PhoneNumberUtil.extractPossibleNumber(number)
        if (PhoneNumberUtil.PLUS_CHARS_PATTERN.matchesAt(possibleNumber, 0)) {
            // Returns false if the number starts with a plus sign. We don't believe dialing the country
            // code before emergency numbers (e.g. +1911) works, but later, if that proves to work, we can
            // add additional logic here to handle it.
            return false
        }
        val metadata = getShortNumberMetadataForRegion(regionCode)
        if (metadata == null || !metadata.hasEmergency()) {
            return false
        }
        val normalizedNumber = PhoneNumberUtil.normalizeDigitsOnly(possibleNumber)
        val allowPrefixMatchForRegion =
            allowPrefixMatch && !REGIONS_WHERE_EMERGENCY_NUMBERS_MUST_BE_EXACT.contains(regionCode)
        return matcherApi.matchNationalNumber(
            normalizedNumber, metadata.emergency!!,
            allowPrefixMatchForRegion
        )
    }

    /**
     * Given a valid short number, determines whether it is carrier-specific (however, nothing is
     * implied about its validity). Carrier-specific numbers may connect to a different end-point, or
     * not connect at all, depending on the user's carrier. If it is important that the number is
     * valid, then its validity must first be checked using [.isValidShortNumber] or
     * [.isValidShortNumberForRegion].
     *
     * @param number  the valid short number to check
     * @return whether the short number is carrier-specific, assuming the input was a valid short
     * number
     */
    fun isCarrierSpecific(number: PhoneNumber): Boolean {
        val regionCodes = getRegionCodesForCountryCode(number.countryCode)
        val regionCode = getRegionCodeForShortNumberFromRegionList(number, regionCodes)
        val nationalNumber = getNationalSignificantNumber(number)
        val phoneMetadata = getShortNumberMetadataForRegion(regionCode)
        return (phoneMetadata != null
                && matchesPossibleNumberAndNationalNumber(
            nationalNumber,
            phoneMetadata.carrierSpecific
        ))
    }

    /**
     * Given a valid short number, determines whether it is carrier-specific when dialed from the
     * given region (however, nothing is implied about its validity). Carrier-specific numbers may
     * connect to a different end-point, or not connect at all, depending on the user's carrier. If
     * it is important that the number is valid, then its validity must first be checked using
     * [.isValidShortNumber] or [.isValidShortNumberForRegion]. Returns false if the
     * number doesn't match the region provided.
     *
     * @param number  the valid short number to check
     * @param regionDialingFrom  the region from which the number is dialed
     * @return  whether the short number is carrier-specific in the provided region, assuming the
     * input was a valid short number
     */
    fun isCarrierSpecificForRegion(number: PhoneNumber, regionDialingFrom: String?): Boolean {
        if (!regionDialingFromMatchesNumber(number, regionDialingFrom)) {
            return false
        }
        val nationalNumber = getNationalSignificantNumber(number)
        val phoneMetadata = getShortNumberMetadataForRegion(regionDialingFrom)
        return (phoneMetadata != null
                && matchesPossibleNumberAndNationalNumber(
            nationalNumber,
            phoneMetadata.carrierSpecific
        ))
    }

    /**
     * Given a valid short number, determines whether it is an SMS service (however, nothing is
     * implied about its validity). An SMS service is where the primary or only intended usage is to
     * receive and/or send text messages (SMSs). This includes MMS as MMS numbers downgrade to SMS if
     * the other party isn't MMS-capable. If it is important that the number is valid, then its
     * validity must first be checked using [.isValidShortNumber] or [ ][.isValidShortNumberForRegion]. Returns false if the number doesn't match the region provided.
     *
     * @param number  the valid short number to check
     * @param regionDialingFrom  the region from which the number is dialed
     * @return  whether the short number is an SMS service in the provided region, assuming the input
     * was a valid short number
     */
    fun isSmsServiceForRegion(number: PhoneNumber, regionDialingFrom: String?): Boolean {
        if (!regionDialingFromMatchesNumber(number, regionDialingFrom)) {
            return false
        }
        val phoneMetadata = getShortNumberMetadataForRegion(regionDialingFrom)
        return (phoneMetadata != null
                && matchesPossibleNumberAndNationalNumber(
            getNationalSignificantNumber(number),
            phoneMetadata.smsServices
        ))
    }

    // TODO: Once we have benchmarked ShortNumberInfo, consider if it is worth keeping
    // this performance optimization.
    private fun matchesPossibleNumberAndNationalNumber(
        number: String,
        numberDesc: Phonemetadata.PhoneNumberDesc?
    ): Boolean {
        return if (numberDesc!!.possibleLengthCount > 0
            && !numberDesc.possibleLengthList.contains(number.length)
        ) {
            false
        } else matcherApi.matchNationalNumber(number, numberDesc, false)
    }

    companion object {
        // In these countries, if extra digits are added to an emergency number, it no longer connects
        // to the emergency service.
        private val REGIONS_WHERE_EMERGENCY_NUMBERS_MUST_BE_EXACT: MutableSet<String?> = HashSet()

        init {
            REGIONS_WHERE_EMERGENCY_NUMBERS_MUST_BE_EXACT.add("BR")
            REGIONS_WHERE_EMERGENCY_NUMBERS_MUST_BE_EXACT.add("CL")
            REGIONS_WHERE_EMERGENCY_NUMBERS_MUST_BE_EXACT.add("NI")
        }

        /**
         * Gets the national significant number of the a phone number. Note a national significant number
         * doesn't contain a national prefix or any formatting.
         *
         *
         * This is a temporary duplicate of the `getNationalSignificantNumber` method from
         * `PhoneNumberUtil`. Ultimately a canonical static version should exist in a separate
         * utility class (to prevent `ShortNumberInfo` needing to depend on PhoneNumberUtil).
         *
         * @param number  the phone number for which the national significant number is needed
         * @return  the national significant number of the PhoneNumber object passed in
         */
        private fun getNationalSignificantNumber(number: PhoneNumber): String {
            // If leading zero(s) have been set, we prefix this now. Note this is not a national prefix.
            val leadingZeros =
                if (number.isItalianLeadingZero) {
                    val zeros = CharArray(number.numberOfLeadingZeros) { '0' }
                    zeros.joinToString("")
                } else {
                    ""
                }
            return "$leadingZeros${number.nationalNumber}"
        }
    }
}
