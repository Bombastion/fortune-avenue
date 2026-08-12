package com.fortuneavenue.server.models.board.db

import com.fortuneavenue.server.models.game.db.GamesTable
import com.fortuneavenue.server.models.player.db.PlayersTable
import org.jetbrains.exposed.v1.core.dao.id.UuidTable

private const val BASE_PRICE_PERCENTAGE_PRECISION = 4
private const val BASE_PRICE_PERCENTAGE_SCALE = 4

/**
 * Per-game copy of a [ShopInformation] row. Board templates are reusable across games, so a
 * shop's mutable in-game state -- current value, owner, investment -- lives here instead, one
 * row per (game, shop), seeded from shop_information when a game starts.
 *
 * See the migration for the constraints enforced at the DB level.
 */
object GameShopInformationTable : UuidTable("game_shop_information") {
	val gameId = reference("game_id", GamesTable)
	val shopInformationId = reference("shop_information_id", ShopInformationTable)
	val boardId = reference("board_id", BoardsTable)
	val spaceId = reference("space_id", BoardSpacesTable)
	val baseValue = integer("base_value")
	val basePricePercentage = decimal("base_price_percentage", BASE_PRICE_PERCENTAGE_PRECISION, BASE_PRICE_PERCENTAGE_SCALE)

	// Nullable, null by default: no player owns the shop until someone buys in.
	val ownerId = optReference("owner_id", PlayersTable)

	val currentValue = integer("current_value")
	val currentInvestment = integer("current_investment")
	val maxCap = integer("max_cap")

	init {
		uniqueIndex(gameId, shopInformationId)
	}
}
