package com.fortuneavenue.server.dao

import com.fortuneavenue.server.models.board.db.BoardSpacesTable
import com.fortuneavenue.server.models.board.db.SpaceType
import com.fortuneavenue.server.models.game.db.GamesTable
import com.fortuneavenue.server.models.player.db.Player
import com.fortuneavenue.server.models.player.db.PlayerState
import com.fortuneavenue.server.models.player.db.PlayerStatesTable
import com.fortuneavenue.server.models.player.db.PlayerStatus
import com.fortuneavenue.server.models.player.db.PlayersTable
import com.fortuneavenue.server.models.user.db.UsersTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository
import kotlin.uuid.Uuid

// Real current gold always starts out as the player's game's board.startingGold (see
// PlayerService.addPlayer) -- this only exists so DAO-level tests that don't care about gold at
// all don't have to invent a number.
private const val DEFAULT_CURRENT_GOLD = 1000

@Repository
class PlayerDao {

	fun create(gameId: Uuid, userId: Uuid? = null, currentGold: Int = DEFAULT_CURRENT_GOLD): Player = transaction {
		val player = Player.new {
			this.gameId = EntityID(gameId, GamesTable)
			this.userId = userId?.let { EntityID(it, UsersTable) }
		}

		// Every player gets state the moment it exists
		PlayerState.new {
			this.playerId = player.id
			this.currentGold = currentGold
		}

		player
	}

	fun findById(id: Uuid): Player? = transaction {
		Player.findById(id)
	}

	fun findByGameId(gameId: Uuid): List<Player> = transaction {
		Player.find { PlayersTable.gameId eq EntityID(gameId, GamesTable) }.toList()
	}

	fun findState(playerId: Uuid): PlayerState? = transaction {
		findStateEntity(playerId)
	}

	fun updateStatus(playerId: Uuid, status: PlayerStatus): PlayerState? = transaction {
		findStateEntity(playerId)?.apply { this.status = status }
	}

	fun updatePosition(playerId: Uuid, spaceId: Uuid): PlayerState? = transaction {
		findStateEntity(playerId)?.apply { this.currentSpaceId = EntityID(spaceId, BoardSpacesTable) }
	}

	/** Adds [delta] (negative to spend) to a player's currentGold. Can go negative -- see PlayerStatesTable. */
	fun adjustGold(playerId: Uuid, delta: Int): PlayerState? = transaction {
		findStateEntity(playerId)?.apply { currentGold += delta }
	}

	/**
	 * Adds [suit] to [playerId]'s held suits if they don't already have it -- a suit already
	 * held has no effect (see GameSimulationService, which drives this whenever a player passes
	 * or lands on a suit space). Returns whether this was actually a new pickup, so a caller can
	 * tell whether to announce it, or null if the player has no state at all.
	 */
	fun addHeldSuit(playerId: Uuid, suit: SpaceType): Boolean? = transaction {
		val state = findStateEntity(playerId) ?: return@transaction null

		if (suit.name in state.heldSuits) {
			false
		} else {
			state.heldSuits = state.heldSuits + suit.name
			true
		}
	}

	/**
	 * Clears [playerId]'s held suits entirely, but only if they currently hold every one of
	 * [requiredSuits] -- a promotion trigger (see GameSimulationService, which drives this
	 * whenever a player passes or lands on a BANK space). Returns whether they actually held
	 * every suit (and so were cleared) -- a player missing even one is left untouched -- or null
	 * if the player has no state at all.
	 */
	fun clearHeldSuitsIfComplete(playerId: Uuid, requiredSuits: Set<SpaceType>): Boolean? = transaction {
		val state = findStateEntity(playerId) ?: return@transaction null

		if (requiredSuits.all { it.name in state.heldSuits }) {
			state.heldSuits = emptyList()
			true
		} else {
			false
		}
	}

	// Not itself wrapped in a transaction -- only ever called from within one
	// of the transaction {} blocks above.
	private fun findStateEntity(playerId: Uuid): PlayerState? =
		PlayerState.find { PlayerStatesTable.playerId eq EntityID(playerId, PlayersTable) }.firstOrNull()
}
