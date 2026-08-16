package com.fortuneavenue.server.models.board.db

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import kotlin.uuid.Uuid

class GameDistrictInformation(id: EntityID<Uuid>) : UuidEntity(id) {
	companion object : UuidEntityClass<GameDistrictInformation>(GameDistrictInformationTable)

	var gameId by GameDistrictInformationTable.gameId
	var districtId by GameDistrictInformationTable.districtId
	var boardId by GameDistrictInformationTable.boardId
	var minimumStockPercentage by GameDistrictInformationTable.minimumStockPercentage
	var currentStockValue by GameDistrictInformationTable.currentStockValue
}
