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

data class DiceRolledEvent(
	val playerId: String,
	val roll: Int,
	override val type: String = "dice_rolled",
) : GameEvent

data class PlayerMovedEvent(
	val turnNumber: Int,
	val playerId: String,
	val fromSpaceId: String,
	val toSpaceId: String,
	val movementPointsRemaining: Int,
	override val type: String = "player_moved",
) : GameEvent

/** One outgoing path a player can pick with a `choose_path` message. */
data class PathOptionPayload(val toSpaceId: String, val branchOrder: Int)

data class ChoiceRequiredEvent(
	val playerId: String,
	val spaceId: String,
	val options: List<PathOptionPayload>,
	override val type: String = "choice_required",
) : GameEvent

data class TurnEndedEvent(
	val turnNumber: Int,
	val playerId: String,
	override val type: String = "turn_ended",
) : GameEvent

/** It's [playerId]'s turn and they need to roll -- nothing else is going to announce this for them. */
data class TurnStartedEvent(
	val playerId: String,
	val turnNumber: Int,
	override val type: String = "turn_started",
) : GameEvent

data class GameOverEvent(
	val turnCount: Int,
	override val type: String = "game_over",
) : GameEvent

data class ErrorEvent(
	val message: String,
	override val type: String = "error",
) : GameEvent
