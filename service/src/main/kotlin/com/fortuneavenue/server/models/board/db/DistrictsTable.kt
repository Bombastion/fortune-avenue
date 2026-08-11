package com.fortuneavenue.server.models.board.db

import org.jetbrains.exposed.v1.core.dao.id.UuidTable

private const val COLOR_HEX_LENGTH = 6

/** Groups related spaces together (e.g. a set of same-colored spaces), see the migration for details. */
object DistrictsTable : UuidTable("districts") {
	val boardId = reference("board_id", BoardsTable)
	val name = varchar("name", 255)
	val colorHex = varchar("color_hex", COLOR_HEX_LENGTH)
}
