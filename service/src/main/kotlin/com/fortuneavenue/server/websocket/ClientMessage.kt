package com.fortuneavenue.server.websocket

/**
 * Every message a client sends is `{"type": "..."}`, plus whatever extra
 * fields that type needs -- gameId/playerId are already known from the
 * connection. `choose_path` is the only one that currently needs more:
 * `{"type": "choose_path", "spaceId": "<id of the space to move onto>"}`.
 */
data class ClientMessage(
	val type: String,
	val spaceId: String? = null,
)

object ClientMessageType {
	const val READY = "ready"
	const val ROLL_DICE = "roll_dice"
	const val CHOOSE_PATH = "choose_path"
}
