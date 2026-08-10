package com.fortuneavenue.server.websocket

/** Everything the server can send back over the socket. Ids are strings -- see the REST response DTOs for why. */
sealed interface GameEvent {
	val type: String
}

data class ConnectedEvent(
	val playerId: String,
	override val type: String = "connected",
) : GameEvent

data class PlayerReadyEvent(
	val playerId: String,
	override val type: String = "player_ready",
) : GameEvent

data class GameStartedEvent(
	val turnOrder: List<String>,
	override val type: String = "game_started",
) : GameEvent

data class TurnTakenEvent(
	val turnNumber: Int,
	val playerId: String,
	val fromSpaceId: String?,
	val toSpaceId: String,
	override val type: String = "turn_taken",
) : GameEvent

data class GameOverEvent(
	val turnCount: Int,
	override val type: String = "game_over",
) : GameEvent

data class ErrorEvent(
	val message: String,
	override val type: String = "error",
) : GameEvent
