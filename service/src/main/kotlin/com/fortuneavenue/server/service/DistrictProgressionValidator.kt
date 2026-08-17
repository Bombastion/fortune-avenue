package com.fortuneavenue.server.service

import com.fortuneavenue.server.models.board.rest.CreateBoardRequest
import com.fortuneavenue.server.models.board.rest.CreateDistrictProgressionRequest
import java.math.BigDecimal

private const val BOOST_PERCENTAGE_SCALE = 4
private const val MIN_SPACES_REQUIRING_PROGRESSIONS = 2

/**
 * Validates a board creation request's district value progressions: a district with at least
 * [MIN_SPACES_REQUIRING_PROGRESSIONS] spaces (per spaces' districtIndex) must define exactly one
 * progression entry for every ownedShopCount from 2 up to that district's total space count -- no
 * gaps, no duplicates, no extras -- and a district with fewer spaces must define none. Every
 * entry's existingShopBoostPercentage/newShopBoostPercentage must be a positive decimal with
 * exactly 4 digits.
 */
object DistrictProgressionValidator {

    fun validate(request: CreateBoardRequest): List<String> {
        val spaceCountByDistrictIndex =
            request.spaces.mapNotNull { it.districtIndex }.groupingBy { it }.eachCount()

        return request.districts.flatMapIndexed { index, district ->
            val spaceCount = spaceCountByDistrictIndex[index] ?: 0
            val requiredLevels =
                if (spaceCount >= MIN_SPACES_REQUIRING_PROGRESSIONS) {
                    (MIN_SPACES_REQUIRING_PROGRESSIONS..spaceCount).toSet()
                } else {
                    emptySet()
                }
            val actualLevels = district.progressions.map { it.ownedShopCount }

            levelErrors(index, spaceCount, requiredLevels, actualLevels) +
                district.progressions.flatMap { percentageErrors(index, it) }
        }
    }

    private fun levelErrors(
        index: Int,
        spaceCount: Int,
        requiredLevels: Set<Int>,
        actualLevels: List<Int>,
    ): List<String> {
        val errors = mutableListOf<String>()

        val duplicates =
            actualLevels.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()
        if (duplicates.isNotEmpty()) {
            errors +=
                "District at index $index defines duplicate progression entries for ownedShopCount $duplicates."
        }

        val actualLevelSet = actualLevels.toSet()
        val missing = (requiredLevels - actualLevelSet).sorted()
        val extra = (actualLevelSet - requiredLevels).sorted()
        if (missing.isNotEmpty() || extra.isNotEmpty()) {
            val missingClause = if (missing.isNotEmpty()) "; missing $missing" else ""
            val extraClause = if (extra.isNotEmpty()) "; unexpected $extra" else ""
            errors +=
                "District at index $index has $spaceCount space(s) and must define exactly one progression entry for each " +
                    "ownedShopCount in $requiredLevels$missingClause$extraClause."
        }

        return errors
    }

    private fun percentageErrors(
        index: Int,
        progression: CreateDistrictProgressionRequest,
    ): List<String> {
        val errors = mutableListOf<String>()

        if (!isValidBoostPercentage(progression.existingShopBoostPercentage)) {
            errors +=
                "District at index $index's progression for ownedShopCount ${progression.ownedShopCount} must have a positive " +
                    "existingShopBoostPercentage with exactly $BOOST_PERCENTAGE_SCALE digits."
        }
        if (!isValidBoostPercentage(progression.newShopBoostPercentage)) {
            errors +=
                "District at index $index's progression for ownedShopCount ${progression.ownedShopCount} must have a positive " +
                    "newShopBoostPercentage with exactly $BOOST_PERCENTAGE_SCALE digits."
        }

        return errors
    }

    private fun isValidBoostPercentage(value: BigDecimal): Boolean =
        value > BigDecimal.ZERO && value.scale() == BOOST_PERCENTAGE_SCALE
}
