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

	// Mirrors the UNIQUE (game_id, user_id) constraint from the migration --
	// stops the same user from being added to a game twice, while still
	// allowing any number of null-user (computer) players per game, since
	// Postgres treats each NULL as distinct for uniqueness purposes.
	init {
		uniqueIndex(gameId, userId)
	}
}
