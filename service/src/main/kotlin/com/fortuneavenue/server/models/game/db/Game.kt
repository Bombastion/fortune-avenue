package com.fortuneavenue.server.models.game.db

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import kotlin.uuid.Uuid

class Game(id: EntityID<Uuid>) : UuidEntity(id) {
	companion object : UuidEntityClass<Game>(GamesTable)

	var boardId by GamesTable.boardId
	var turnNumber by GamesTable.turnNumber
	var turnOrder by GamesTable.turnOrder
	var maxTurns by GamesTable.maxTurns
	var currentMovementPoints by GamesTable.currentMovementPoints
}
