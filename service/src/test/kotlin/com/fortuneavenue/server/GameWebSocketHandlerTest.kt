package com.fortuneavenue.server

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@SpringBootTest(
	webEnvironment = WebEnvironment.DEFINED_PORT,
	properties = ["server.port=18099"]
)
class GameWebSocketHandlerTest {

	private val testPort = 18099

	@Test
	fun `websocket sends a connected message on open`() {
		val received = CompletableFuture<String>()
		val client = StandardWebSocketClient()

		val handler = object : TextWebSocketHandler() {
			override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
				received.complete(message.payload)
			}
		}

		client.execute(handler, WebSocketHttpHeaders(), URI("ws://localhost:$testPort/ws/game"))

		val message = received.get(5, TimeUnit.SECONDS)
		assertThat(message).startsWith("connected:")
	}
}
