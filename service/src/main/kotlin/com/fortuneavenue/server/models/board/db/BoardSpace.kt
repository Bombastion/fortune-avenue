package com.fortuneavenue.server.models.board.db

import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass

class BoardSpace(id: EntityID<Uuid>) : UuidEntity(id) {
    companion object : UuidEntityClass<BoardSpace>(BoardSpacesTable)

    var boardId by BoardSpacesTable.boardId
    var spaceType by BoardSpacesTable.spaceType
    var districtId by BoardSpacesTable.districtId
}
