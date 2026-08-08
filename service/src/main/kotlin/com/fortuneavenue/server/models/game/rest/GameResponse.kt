package com.fortuneavenue.server.models.game.rest

import com.fortuneavenue.server.models.game.db.Game

data class GameResponse(
	val id: String,
)

fun Game.toResponse(): GameResponse = GameResponse(
	id = id.value.toString(),
)
