package com.fortuneavenue.server.models.board.db

import com.fortuneavenue.server.models.game.db.GamesTable
import org.jetbrains.exposed.v1.core.dao.id.UuidTable

/** Per-game copy of a district's stock information */
object GameDistrictInformationTable : UuidTable("game_district_information") {
    val gameId = reference("game_id", GamesTable)
    val districtId = reference("district_id", DistrictsTable)
    val boardId = reference("board_id", BoardsTable)

    // The district's stock value: the average currentValue of its SHOP spaces at seed time
    // (equal to baseValue that early), scaled by the fixed multiplier described on
    // GameDistrictInformationDao.computeCurrentStockValue -- see
    // GameDistrictInformationDao.seedForGame.
    val currentStockValue = integer("current_stock_value")

    init {
        uniqueIndex(gameId, districtId)
    }
}
