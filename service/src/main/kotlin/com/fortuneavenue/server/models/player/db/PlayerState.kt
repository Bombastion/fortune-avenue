package com.fortuneavenue.server.models.player.db

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import kotlin.uuid.Uuid

class PlayerState(id: EntityID<Uuid>) : UuidEntity(id) {
	companion object : UuidEntityClass<PlayerState>(PlayerStatesTable)

	var playerId by PlayerStatesTable.playerId
	var currentSpaceId by PlayerStatesTable.currentSpaceId
	var status by PlayerStatesTable.status
	var currentGold by PlayerStatesTable.currentGold
	var heldSuits by PlayerStatesTable.heldSuits
	var promotionCount by PlayerStatesTable.promotionCount
}
