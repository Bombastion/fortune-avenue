package com.fortuneavenue.server.dao

import com.fortuneavenue.server.models.board.db.GameDistrictInformationTable
import com.fortuneavenue.server.models.player.db.PlayerStock
import com.fortuneavenue.server.models.player.db.PlayerStocksTable
import com.fortuneavenue.server.models.player.db.PlayersTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository
import kotlin.uuid.Uuid

@Repository
class PlayerStockDao {

	fun find(playerId: Uuid, gameDistrictInformationId: Uuid): PlayerStock? = transaction {
		findEntity(playerId, gameDistrictInformationId)
	}

	/** Every district [playerId] currently holds any stock in (or has ever traded), across their one game. */
	fun findByPlayer(playerId: Uuid): List<PlayerStock> = transaction {
		PlayerStock.find { PlayerStocksTable.playerId eq EntityID(playerId, PlayersTable) }.toList()
	}

	/**
	 * Adds [delta] (negative to sell) to [playerId]'s held quantity of [gameDistrictInformationId]'s
	 * stock, creating the row (starting from 0) the first time this player trades that district's
	 * stock. Callers are responsible for keeping the result non-negative (see
	 * PlayerStocksTable) -- GameSimulationService never lets a sale exceed what's currently held.
	 */
	fun adjustQuantity(playerId: Uuid, gameDistrictInformationId: Uuid, delta: Int): PlayerStock = transaction {
		val existing = findEntity(playerId, gameDistrictInformationId)
		if (existing != null) {
			existing.apply { quantity += delta }
		} else {
			PlayerStock.new {
				this.playerId = EntityID(playerId, PlayersTable)
				this.gameDistrictInformationId = EntityID(gameDistrictInformationId, GameDistrictInformationTable)
				this.quantity = delta
			}
		}
	}

	// Not itself wrapped in a transaction -- only ever called from within one of the
	// transaction {} blocks above.
	private fun findEntity(playerId: Uuid, gameDistrictInformationId: Uuid): PlayerStock? =
		PlayerStock.find {
			(PlayerStocksTable.playerId eq EntityID(playerId, PlayersTable)) and
				(PlayerStocksTable.gameDistrictInformationId eq EntityID(gameDistrictInformationId, GameDistrictInformationTable))
		}.firstOrNull()
}
