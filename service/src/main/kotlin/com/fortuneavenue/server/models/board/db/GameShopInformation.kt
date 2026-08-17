package com.fortuneavenue.server.models.board.db

import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass

class GameShopInformation(id: EntityID<Uuid>) : UuidEntity(id) {
    companion object : UuidEntityClass<GameShopInformation>(GameShopInformationTable)

    var gameId by GameShopInformationTable.gameId
    var shopInformationId by GameShopInformationTable.shopInformationId
    var boardId by GameShopInformationTable.boardId
    var spaceId by GameShopInformationTable.spaceId
    var baseValue by GameShopInformationTable.baseValue
    var basePricePercentage by GameShopInformationTable.basePricePercentage
    var ownerId by GameShopInformationTable.ownerId
    var districtId by GameShopInformationTable.districtId
    var currentValue by GameShopInformationTable.currentValue
    var currentInvestment by GameShopInformationTable.currentInvestment
    var maxCap by GameShopInformationTable.maxCap
}
