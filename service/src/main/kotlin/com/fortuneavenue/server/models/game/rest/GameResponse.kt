package com.fortuneavenue.server.models.game.rest

import com.fortuneavenue.server.models.game.db.Game

data class GameResponse(
    val id: String,
    val boardId: String,
    val targetNetWorth: Int,
)

fun Game.toResponse(): GameResponse =
    GameResponse(
        id = id.value.toString(),
        boardId = boardId.value.toString(),
        targetNetWorth = targetNetWorth,
    )
