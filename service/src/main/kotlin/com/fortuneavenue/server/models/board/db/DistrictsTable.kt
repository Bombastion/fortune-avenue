package com.fortuneavenue.server.models.board.db

import org.jetbrains.exposed.v1.core.dao.id.UuidTable

private const val COLOR_HEX_LENGTH = 6
private const val STOCK_PERCENTAGE_PRECISION = 4
private const val STOCK_PERCENTAGE_SCALE = 4

/** Groups related spaces together (e.g. a set of same-colored spaces), see the migration for details. */
object DistrictsTable : UuidTable("districts") {
	val boardId = reference("board_id", BoardsTable)
	val name = varchar("name", 255)
	val colorHex = varchar("color_hex", COLOR_HEX_LENGTH)

	// The floor, as a fraction of the average value of the district's SHOP spaces, that its
	// stock can trade at once a game starts -- see GameDistrictInformationTable.currentStockValue,
	// seeded from this when a game starts.
	val minimumStockPercentage = decimal("minimum_stock_percentage", STOCK_PERCENTAGE_PRECISION, STOCK_PERCENTAGE_SCALE)
}
