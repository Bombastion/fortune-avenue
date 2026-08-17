package com.fortuneavenue.server.dao

import com.fortuneavenue.server.models.board.db.BoardGraph
import com.fortuneavenue.server.models.board.db.BoardSpacesTable
import com.fortuneavenue.server.models.board.db.GameShopInformation
import com.fortuneavenue.server.models.board.db.GameShopInformationTable
import com.fortuneavenue.server.models.game.db.GamesTable
import com.fortuneavenue.server.models.player.db.PlayersTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository
import kotlin.uuid.Uuid

@Repository
class GameShopInformationDao {

	/**
	 * Seeds one row per SHOP space on [boardGraph], copying board_id/space_id/base_value/
	 * base_price_percentage/district_id from the board's (reusable) shop_information/board_spaces
	 * rows -- current_value starts at base_value, current_investment at 0, and owner_id is null
	 * until a player buys in. Called once, when a game actually starts (see
	 * GameSimulationService.markReady).
	 *
	 * max_cap doesn't have a real formula yet (that's investment mechanics, not implemented) --
	 * it's seeded equal to base_value as an inert placeholder.
	 */
	fun seedForGame(gameId: Uuid, boardGraph: BoardGraph): List<GameShopInformation> = transaction {
		val spacesById = boardGraph.spaces.associateBy { it.id.value }

		boardGraph.shopInformation.map { shopInfo ->
			GameShopInformation.new {
				this.gameId = EntityID(gameId, GamesTable)
				shopInformationId = shopInfo.id
				boardId = shopInfo.boardId
				spaceId = shopInfo.spaceId
				baseValue = shopInfo.baseValue
				basePricePercentage = shopInfo.basePricePercentage
				districtId = spacesById[shopInfo.spaceId.value]?.districtId
				currentValue = shopInfo.baseValue
				currentInvestment = 0
				maxCap = shopInfo.baseValue
			}
		}
	}

	fun findByGameAndSpace(gameId: Uuid, spaceId: Uuid): GameShopInformation? = transaction {
		GameShopInformation.find {
			(GameShopInformationTable.gameId eq EntityID(gameId, GamesTable)) and
				(GameShopInformationTable.spaceId eq EntityID(spaceId, BoardSpacesTable))
		}.firstOrNull()
	}

	fun findOwnedByPlayerInDistrict(gameId: Uuid, playerId: Uuid, districtId: EntityID<Uuid>): List<GameShopInformation> = transaction {
		GameShopInformation.find {
			(GameShopInformationTable.gameId eq EntityID(gameId, GamesTable)) and
				(GameShopInformationTable.ownerId eq EntityID(playerId, PlayersTable)) and
				(GameShopInformationTable.districtId eq districtId)
		}.toList()
	}

	/**
	 * Every shop [playerId] owns in [gameId], across every district (or none) -- see
	 * GameSimulationService's BANK promotion payout, which sums these shops' currentValue.
	 * Unlike [findOwnedByPlayerInDistrict], not scoped to a single district.
	 */
	fun findOwnedByPlayer(gameId: Uuid, playerId: Uuid): List<GameShopInformation> = transaction {
		GameShopInformation.find {
			(GameShopInformationTable.gameId eq EntityID(gameId, GamesTable)) and
				(GameShopInformationTable.ownerId eq EntityID(playerId, PlayersTable))
		}.toList()
	}

	/**
	 * Every shop in [districtId], regardless of owner (or lack of one)
	 */
	fun findByGameAndDistrict(gameId: Uuid, districtId: EntityID<Uuid>): List<GameShopInformation> = transaction {
		GameShopInformation.find {
			(GameShopInformationTable.gameId eq EntityID(gameId, GamesTable)) and
				(GameShopInformationTable.districtId eq districtId)
		}.toList()
	}

	fun setOwner(id: Uuid, playerId: Uuid): GameShopInformation? = transaction {
		GameShopInformation.findById(id)?.apply { ownerId = EntityID(playerId, PlayersTable) }
	}

	fun setCurrentValue(id: Uuid, currentValue: Int): GameShopInformation? = transaction {
		GameShopInformation.findById(id)?.apply { this.currentValue = currentValue }
	}
}
