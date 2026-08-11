package com.fortuneavenue.server.service

import com.fortuneavenue.server.models.board.db.SpaceType
import com.fortuneavenue.server.models.board.rest.CreateBoardSpaceRequest
import java.math.BigDecimal

private const val BASE_PRICE_PERCENTAGE_SCALE = 4

/**
 * Validates the SHOP-specific fields on a board creation request: SHOP spaces must carry a
 * positive [CreateBoardSpaceRequest.baseValue] and a [CreateBoardSpaceRequest.basePricePercentage]
 * strictly between 0 and 1 with exactly 4 digits; every other space type must omit both.
 */
object ShopSpaceValidator {

	fun validate(spaces: List<CreateBoardSpaceRequest>): List<String> = spaces.mapIndexedNotNull { index, space ->
		if (space.spaceType == SpaceType.SHOP) {
			shopSpaceError(index, space)
		} else {
			nonShopSpaceError(index, space)
		}
	}

	private fun shopSpaceError(index: Int, space: CreateBoardSpaceRequest): String? {
		val problems = mutableListOf<String>()

		val baseValue = space.baseValue
		if (baseValue == null || baseValue <= 0) {
			problems += "a positive baseValue"
		}

		val basePricePercentage = space.basePricePercentage
		if (basePricePercentage == null) {
			problems += "a basePricePercentage"
		} else {
			if (basePricePercentage <= BigDecimal.ZERO || basePricePercentage >= BigDecimal.ONE) {
				problems += "a basePricePercentage strictly between 0 and 1"
			}
			if (basePricePercentage.scale() != BASE_PRICE_PERCENTAGE_SCALE) {
				problems += "a basePricePercentage with exactly $BASE_PRICE_PERCENTAGE_SCALE digits"
			}
		}

		return if (problems.isEmpty()) null else "Space at index $index is a SHOP space and must have ${problems.joinToString(" and ")}."
	}

	private fun nonShopSpaceError(index: Int, space: CreateBoardSpaceRequest): String? {
		val problems = mutableListOf<String>()
		if (space.baseValue != null) problems += "baseValue"
		if (space.basePricePercentage != null) problems += "basePricePercentage"

		return if (problems.isEmpty()) {
			null
		} else {
			"Space at index $index is not a SHOP space and must not include ${problems.joinToString(" or ")}."
		}
	}
}
