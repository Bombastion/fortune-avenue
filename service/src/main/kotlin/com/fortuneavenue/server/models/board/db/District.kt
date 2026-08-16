package com.fortuneavenue.server.models.board.db

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import kotlin.uuid.Uuid

class District(id: EntityID<Uuid>) : UuidEntity(id) {
	companion object : UuidEntityClass<District>(DistrictsTable)

	var boardId by DistrictsTable.boardId
	var name by DistrictsTable.name
	var colorHex by DistrictsTable.colorHex
	var minimumStockPercentage by DistrictsTable.minimumStockPercentage
}
