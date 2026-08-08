package com.fortuneavenue.server.models.game.db

import com.fortuneavenue.server.models.board.db.BoardsTable
import org.jetbrains.exposed.v1.core.dao.id.UuidTable

// Otherwise minimal for now -- just enough of a real table for players (and
// now a board) to reference. Whatever else a game actually needs (turn
// state, status, ...) lands here once that's built.
object GamesTable : UuidTable("games") {
	val boardId = reference("board_id", BoardsTable)
}
