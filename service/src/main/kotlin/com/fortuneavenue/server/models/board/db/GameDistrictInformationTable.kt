package com.fortuneavenue.server.models.board.db

import com.fortuneavenue.server.models.game.db.GamesTable
import org.jetbrains.exposed.v1.core.dao.id.UuidTable

// 5, not 4 -- same reasoning as DistrictsTable.minimumStockPercentage: this is allowed to
// equal 1 exactly.
private const val STOCK_PERCENTAGE_PRECISION = 5
private const val STOCK_PERCENTAGE_SCALE = 4

/**
 * Per-game copy of a district's stock information. Board templates are reusable across games, so
 * minimum_stock_percentage is denormalized here from districts -- same reasoning as
 * GameShopInformationTable copying shop_information's fields. One row per (game, district),
 * seeded from the district's minimumStockPercentage and its SHOP spaces' just-seeded
 * current_value when a game starts (see GameDistrictInformationDao.seedForGame) -- only for
 * districts that actually contain at least one SHOP space.
 *
 * See the migration for the constraints enforced at the DB level.
 */
object GameDistrictInformationTable : UuidTable("game_district_information") {
	val gameId = reference("game_id", GamesTable)
	val districtId = reference("district_id", DistrictsTable)
	val boardId = reference("board_id", BoardsTable)
	val minimumStockPercentage = decimal("minimum_stock_percentage", STOCK_PERCENTAGE_PRECISION, STOCK_PERCENTAGE_SCALE)

	// The district's stock value: the average currentValue of its SHOP spaces at seed time
	// (equal to baseValue that early), multiplied by minimumStockPercentage -- see
	// GameDistrictInformationDao.seedForGame.
	val currentStockValue = integer("current_stock_value")

	init {
		uniqueIndex(gameId, districtId)
	}
}
