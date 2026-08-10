package com.fortuneavenue.server.models.game.db

import com.fortuneavenue.server.models.board.db.BoardsTable
import org.jetbrains.exposed.v1.core.dao.id.UuidTable

// Otherwise minimal for now -- players and a board to reference, plus turn
// number. Whatever else a game actually needs (status, ...) lands here once
// that's built.
object GamesTable : UuidTable("games") {
	val boardId = reference("board_id", BoardsTable)

	// Backed by a SQL-level DEFAULT 0, not this .default(0) -- Exposed only
	// sends columns present in an entity's writeValues on insert, so as long
	// as nothing explicitly sets turnNumber when creating a game, it's simply
	// left out of the INSERT and Postgres' own default fills it in. The
	// .default(0) here mainly documents that and covers in-memory reads
	// before the entity is flushed.
	val turnNumber = integer("turn_number").default(0)
}
