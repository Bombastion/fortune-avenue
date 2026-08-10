package com.fortuneavenue.server.models.game.db

import com.fortuneavenue.server.models.board.db.BoardsTable
import org.jetbrains.exposed.v1.core.dao.id.UuidTable

// Otherwise minimal for now -- players and a board to reference, plus turn
// number. Whatever else a game actually needs (status, ...) lands here once
// that's built.
object GamesTable : UuidTable("games") {
	val boardId = reference("board_id", BoardsTable)

	// Defaults to 0, indicated pre-game
	val turnNumber = integer("turn_number").default(0)
}
