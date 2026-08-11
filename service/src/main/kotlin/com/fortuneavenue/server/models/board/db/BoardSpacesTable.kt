package com.fortuneavenue.server.models.board.db

import org.jetbrains.exposed.v1.core.dao.id.UuidTable

private const val SPACE_TYPE_LENGTH = 50

object BoardSpacesTable : UuidTable("board_spaces") {
	val boardId = reference("board_id", BoardsTable)
	val spaceType = enumerationByName("space_type", SPACE_TYPE_LENGTH, SpaceType::class)

	// Nullable: not every space belongs to a district.
	val districtId = optReference("district_id", DistrictsTable)
}
