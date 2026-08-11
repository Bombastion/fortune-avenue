package com.fortuneavenue.server.service

import com.fortuneavenue.server.models.board.rest.CreateBoardRequest

private val COLOR_HEX_PATTERN = Regex("^[0-9A-Fa-f]{6}$")

/**
 * Validates a board creation request's districts: every district's colorHex must be exactly 6 hex
 * characters, and every space's districtIndex (if present) must reference a real district.
 */
object DistrictValidator {

	fun validate(request: CreateBoardRequest): List<String> {
		val districtErrors = request.districts.mapIndexedNotNull { index, district ->
			if (COLOR_HEX_PATTERN.matches(district.colorHex)) {
				null
			} else {
				"District at index $index has colorHex '${district.colorHex}'; it must be exactly 6 hex characters (0-9, A-F)."
			}
		}

		val districtCount = request.districts.size
		val spaceErrors = request.spaces.mapIndexedNotNull { index, space ->
			val districtIndex = space.districtIndex
			if (districtIndex == null || districtIndex in 0 until districtCount) {
				null
			} else {
				"Space at index $index references district index $districtIndex, which is out of range for $districtCount district(s)."
			}
		}

		return districtErrors + spaceErrors
	}
}
