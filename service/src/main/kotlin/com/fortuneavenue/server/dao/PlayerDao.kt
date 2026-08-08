package com.fortuneavenue.server.dao

import com.fortuneavenue.server.models.game.db.GamesTable
import com.fortuneavenue.server.models.player.db.Player
import com.fortuneavenue.server.models.player.db.PlayersTable
import com.fortuneavenue.server.models.user.db.UsersTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository
import kotlin.uuid.Uuid

/**
 * [userId] is optional -- a player doesn't have to be tied to a person.
 * A null user is how a computer opponent (not implemented yet) will be
 * represented once it exists.
 *
 * Unlike BoardDao, create() here never needs an explicit flush() to control
 * statement ordering: gameId and userId always point at rows from a
 * *previous, already-committed* transaction (the caller creates the game --
 * and optionally the user -- first), never at something being inserted in
 * this same transaction. So there's no ordering for Exposed to get wrong.
 */
@Repository
class PlayerDao {

	fun create(gameId: Uuid, userId: Uuid? = null): Player = transaction {
		Player.new {
			this.gameId = EntityID(gameId, GamesTable)
			this.userId = userId?.let { EntityID(it, UsersTable) }
		}
	}

	fun findById(id: Uuid): Player? = transaction {
		Player.findById(id)
	}

	fun findByGameId(gameId: Uuid): List<Player> = transaction {
		Player.find { PlayersTable.gameId eq EntityID(gameId, GamesTable) }.toList()
	}
}
