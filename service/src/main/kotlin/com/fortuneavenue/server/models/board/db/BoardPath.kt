package com.fortuneavenue.server.models.board.db

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import kotlin.uuid.Uuid

class BoardPath(id: EntityID<Uuid>) : UuidEntity(id) {
	companion object : UuidEntityClass<BoardPath>(BoardPathsTable)

	var boardId by BoardPathsTable.boardId
	var fromSpaceId by BoardPathsTable.fromSpaceId
	var toSpaceId by BoardPathsTable.toSpaceId
	var branchOrder by BoardPathsTable.branchOrder
}
