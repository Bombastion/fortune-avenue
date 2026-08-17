package com.fortuneavenue.server.models.player.db

import com.fortuneavenue.server.models.board.db.GameDistrictInformationTable
import org.jetbrains.exposed.v1.core.dao.id.UuidTable

/** How much of a district's stock a player owns in a given game */
object PlayerStocksTable : UuidTable("player_stocks") {
    val playerId = reference("player_id", PlayersTable)
    val gameDistrictInformationId =
        reference("game_district_information_id", GameDistrictInformationTable)
    val quantity = integer("quantity").default(0)

    init {
        uniqueIndex(playerId, gameDistrictInformationId)
    }
}
