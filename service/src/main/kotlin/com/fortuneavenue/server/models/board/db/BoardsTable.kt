package com.fortuneavenue.server.models.board.db

import org.jetbrains.exposed.v1.core.dao.id.UuidTable

object BoardsTable : UuidTable("boards") {
	val name = varchar("name", 255)

	// Deliberately not a typed reference() to BoardSpacesTable: boards.start_space_id
	// and board_spaces.board_id form a dependency loop (see the migration, where the
	// boards -> board_spaces FK is added via ALTER TABLE after board_spaces exists),
	// and two Exposed Table objects can't cleanly reference each other in their
	// initializers. The FK still exists at the database level, however
	val startSpaceId = uuid("start_space_id").nullable()

	// How much gold every player starts a game on this board with -- see player_states.current_gold.
	val startingGold = integer("starting_gold")
}
