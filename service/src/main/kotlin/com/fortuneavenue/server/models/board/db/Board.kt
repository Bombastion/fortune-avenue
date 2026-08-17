package com.fortuneavenue.server.models.board.db

import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass

class Board(id: EntityID<Uuid>) : UuidEntity(id) {
    companion object : UuidEntityClass<Board>(BoardsTable)

    var name by BoardsTable.name
    var startSpaceId by BoardsTable.startSpaceId
    var startingGold by BoardsTable.startingGold
    var baseSalary by BoardsTable.baseSalary
    var promotionBonus by BoardsTable.promotionBonus
}
