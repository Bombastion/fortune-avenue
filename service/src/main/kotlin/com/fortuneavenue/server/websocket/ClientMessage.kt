package com.fortuneavenue.server.websocket

/** Every message a client sends is just `{"type": "..."}` -- gameId/playerId are already known from the connection. */
data class ClientMessage(
	val type: String,
)

object ClientMessageType {
	const val READY = "ready"
	const val TAKE_TURN = "take_turn"
}
