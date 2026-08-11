package com.fortuneavenue.server.models.board.db

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import kotlin.uuid.Uuid

class ShopInformation(id: EntityID<Uuid>) : UuidEntity(id) {
	companion object : UuidEntityClass<ShopInformation>(ShopInformationTable)

	var boardId by ShopInformationTable.boardId
	var spaceId by ShopInformationTable.spaceId
	var baseValue by ShopInformationTable.baseValue
	var basePricePercentage by ShopInformationTable.basePricePercentage
}
