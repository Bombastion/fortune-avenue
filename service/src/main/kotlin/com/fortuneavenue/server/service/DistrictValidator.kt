package com.fortuneavenue.server.service

import com.fortuneavenue.server.models.board.rest.CreateBoardRequest
import java.math.BigDecimal

private val COLOR_HEX_PATTERN = Regex("^[0-9A-Fa-f]{6}$")
private const val STOCK_PERCENTAGE_SCALE = 4

/**
 * Validates a board creation request's districts: every district's colorHex must be exactly 6 hex
 * characters, every district's minimumStockPercentage must be a positive decimal strictly less than
 * 1 with exactly 4 digits, and every space's districtIndex (if present) must reference a real
 * district.
 */
object DistrictValidator {

    fun validate(request: CreateBoardRequest): List<String> {
        val colorHexErrors =
            request.districts.mapIndexedNotNull { index, district ->
                if (COLOR_HEX_PATTERN.matches(district.colorHex)) {
                    null
                } else {
                    "District at index $index has colorHex '${district.colorHex}'; it must be exactly 6 hex characters (0-9, A-F)."
                }
            }

        val stockPercentageErrors =
            request.districts.mapIndexedNotNull { index, district ->
                if (isValidStockPercentage(district.minimumStockPercentage)) {
                    null
                } else {
                    "District at index $index has minimumStockPercentage ${district.minimumStockPercentage}; it must be a positive " +
                        "decimal strictly between 0 and 1, with exactly $STOCK_PERCENTAGE_SCALE digits."
                }
            }

        val districtCount = request.districts.size
        val spaceErrors =
            request.spaces.mapIndexedNotNull { index, space ->
                val districtIndex = space.districtIndex
                if (districtIndex == null || districtIndex in 0 until districtCount) {
                    null
                } else {
                    "Space at index $index references district index $districtIndex, which is out of range for $districtCount district(s)."
                }
            }

        return colorHexErrors + stockPercentageErrors + spaceErrors
    }

    private fun isValidStockPercentage(value: BigDecimal): Boolean =
        value > BigDecimal.ZERO && value < BigDecimal.ONE && value.scale() == STOCK_PERCENTAGE_SCALE
}
