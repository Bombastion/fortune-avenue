package com.fortuneavenue.server.models.player.rest

import com.fortuneavenue.server.models.player.db.Player

data class PlayerResponse(
    val id: String,
    val gameId: String,
    val userId: String?,
)

fun Player.toResponse(): PlayerResponse =
    PlayerResponse(
        id = id.value.toString(),
        gameId = gameId.value.toString(),
        userId = userId?.value?.toString(),
    )
