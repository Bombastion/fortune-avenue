package com.fortuneavenue.server

import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

/**
 * Minimal scaffold handler — echoes messages back so the wiring can be
 * verified end to end. Deliberately keeps no per-connection game state in
 * instance fields: once real game logic lands, session/game state should
 * live somewhere shareable (e.g. an external store) rather than in this
 * instance's memory, so the server can eventually run as multiple
 * stateless replicas.
 */
@Component
class GameWebSocketHandler : TextWebSocketHandler() {

	override fun afterConnectionEstablished(session: WebSocketSession) {
		session.sendMessage(TextMessage("connected:${session.id}"))
	}

	override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
		session.sendMessage(TextMessage("echo:${message.payload}"))
	}
}
