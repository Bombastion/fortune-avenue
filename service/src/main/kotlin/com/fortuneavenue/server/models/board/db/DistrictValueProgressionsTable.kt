package com.fortuneavenue.server.models.board.db

import org.jetbrains.exposed.v1.core.dao.id.UuidTable

private const val BOOST_PERCENTAGE_PRECISION = 4
private const val BOOST_PERCENTAGE_SCALE = 4

/**
 * How shop values in a district scale as a single player accumulates more of them there. One row
 * per step -- see the migration for the exact mechanics and the constraints enforced at the DB
 * level. Deliberately per-district (not a shared board-level curve): same-sized districts can
 * still scale differently from each other if board makers desire.
 */
object DistrictValueProgressionsTable : UuidTable("district_value_progressions") {
	val districtId = reference("district_id", DistrictsTable)

	// The count of shops in the district the player has just reached (2, 3, 4, ...). Never 1 --
	// owning a single shop in a district has nothing to boost off of yet.
	val ownedShopCount = integer("owned_shop_count")

	// Applied to every shop the player already owned in the district before this purchase.
	val existingShopBoostPercentage = decimal("existing_shop_boost_percentage", BOOST_PERCENTAGE_PRECISION, BOOST_PERCENTAGE_SCALE)

	// Applied to the shop just purchased instead -- larger than existingShopBoostPercentage,
	// since it missed out on the boosts from earlier purchases in the district.
	val newShopBoostPercentage = decimal("new_shop_boost_percentage", BOOST_PERCENTAGE_PRECISION, BOOST_PERCENTAGE_SCALE)

	init {
		uniqueIndex(districtId, ownedShopCount)
	}
}
