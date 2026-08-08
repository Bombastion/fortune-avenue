package com.fortuneavenue.server.models.board.db

import org.jetbrains.exposed.v1.core.dao.id.UuidTable

object BoardsTable : UuidTable("boards") {
	val name = varchar("name", 255)

	// Deliberately not a typed reference() to BoardSpacesTable: boards.start_space_id
	// and board_spaces.board_id form a genuine cycle (see the migration, where the
	// boards -> board_spaces FK is added via ALTER TABLE after board_spaces exists),
	// and two Exposed Table objects can't cleanly reference each other in their
	// initializers. The FK still exists at the database level; Exposed just treats
	// this one column as a plain UUID value rather than a typed join target.
	val startSpaceId = uuid("start_space_id").nullable()
}
