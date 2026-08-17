package com.fortuneavenue.server.models.board.db

import com.fortuneavenue.server.models.game.db.GamesTable
import org.jetbrains.exposed.v1.core.dao.id.UuidTable

private const val STOCK_PERCENTAGE_PRECISION = 4
private const val STOCK_PERCENTAGE_SCALE = 4

/** Per-game copy of a district's stock information */
object GameDistrictInformationTable : UuidTable("game_district_information") {
    val gameId = reference("game_id", GamesTable)
    val districtId = reference("district_id", DistrictsTable)
    val boardId = reference("board_id", BoardsTable)
    val minimumStockPercentage =
        decimal("minimum_stock_percentage", STOCK_PERCENTAGE_PRECISION, STOCK_PERCENTAGE_SCALE)

    // The district's stock value: the average currentValue of its SHOP spaces at seed time
    // (equal to baseValue that early), multiplied by minimumStockPercentage -- see
    // GameDistrictInformationDao.seedForGame.
    val currentStockValue = integer("current_stock_value")

    init {
        uniqueIndex(gameId, districtId)
    }
}
