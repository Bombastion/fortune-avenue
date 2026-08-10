package com.fortuneavenue.server.dao

import com.fortuneavenue.server.models.board.db.BoardSpacesTable
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

@Repository
class PlayerDao {

	fun create(gameId: Uuid, userId: Uuid? = null): Player = transaction {
		val player = Player.new {
			this.gameId = EntityID(gameId, GamesTable)
			this.userId = userId?.let { EntityID(it, UsersTable) }
		}

		// Every player gets state the moment it exists
		PlayerState.new {
			this.playerId = player.id
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

	// Not itself wrapped in a transaction -- only ever called from within one
	// of the transaction {} blocks above.
	private fun findStateEntity(playerId: Uuid): PlayerState? =
		PlayerState.find { PlayerStatesTable.playerId eq EntityID(playerId, PlayersTable) }.firstOrNull()
}
