package com.fortuneavenue.server.models.board.db

import org.jetbrains.exposed.v1.core.dao.id.UuidTable

object BoardPathsTable : UuidTable("board_paths") {
	val boardId = reference("board_id", BoardsTable)
	val fromSpaceId = reference("from_space_id", BoardSpacesTable)
	val toSpaceId = reference("to_space_id", BoardSpacesTable)
	val branchOrder = integer("branch_order").default(0)

	init {
		uniqueIndex(fromSpaceId, branchOrder)
	}
}
