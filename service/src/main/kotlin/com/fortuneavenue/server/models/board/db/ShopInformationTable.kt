package com.fortuneavenue.server.models.board.db

import org.jetbrains.exposed.v1.core.dao.id.UuidTable

private const val BASE_PRICE_PERCENTAGE_PRECISION = 4
private const val BASE_PRICE_PERCENTAGE_SCALE = 4

/** Extra information for SHOP spaces -- see the migration for the constraints enforced at the DB level. */
object ShopInformationTable : UuidTable("shop_information") {
	val boardId = reference("board_id", BoardsTable)
	val spaceId = reference("space_id", BoardSpacesTable)
	val baseValue = integer("base_value")
	val basePricePercentage = decimal("base_price_percentage", BASE_PRICE_PERCENTAGE_PRECISION, BASE_PRICE_PERCENTAGE_SCALE)

	init {
		uniqueIndex(spaceId)
	}
}
