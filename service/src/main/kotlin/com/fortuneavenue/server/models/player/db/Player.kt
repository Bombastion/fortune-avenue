package com.fortuneavenue.server.models.player.db

import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass

class Player(id: EntityID<Uuid>) : UuidEntity(id) {
    companion object : UuidEntityClass<Player>(PlayersTable)

    var gameId by PlayersTable.gameId
    var userId by PlayersTable.userId
}
