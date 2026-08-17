package com.fortuneavenue.server.models.player.db

import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass

class PlayerStock(id: EntityID<Uuid>) : UuidEntity(id) {
    companion object : UuidEntityClass<PlayerStock>(PlayerStocksTable)

    var playerId by PlayerStocksTable.playerId
    var gameDistrictInformationId by PlayerStocksTable.gameDistrictInformationId
    var quantity by PlayerStocksTable.quantity
}
