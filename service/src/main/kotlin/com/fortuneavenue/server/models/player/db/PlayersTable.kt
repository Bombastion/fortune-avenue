package com.fortuneavenue.server.models.player.db

import com.fortuneavenue.server.models.game.db.GamesTable
import com.fortuneavenue.server.models.user.db.UsersTable
import org.jetbrains.exposed.v1.core.dao.id.UuidTable

object PlayersTable : UuidTable("players") {
	val gameId = reference("game_id", GamesTable)

	// Nullable: a player represents a seat in a game, which may or may not be
	// occupied by an actual person. A null user is how a computer opponent
	// (not implemented yet) will be represented once it exists.
	val userId = optReference("user_id", UsersTable)

	init {
		uniqueIndex(gameId, userId)
	}
}
