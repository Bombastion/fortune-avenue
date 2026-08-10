package com.fortuneavenue.server.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.fortuneavenue.server.dao.PlayerDao
import com.fortuneavenue.server.service.GameSimulationService
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import org.springframework.web.util.UriComponentsBuilder
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

private data class Connection(val gameId: Uuid, val playerId: Uuid)

/**
 * Connect as `/ws/game?gameId=<id>&playerId=<id>`. The connection is closed
 * immediately if either id is missing/malformed, or playerId doesn't refer
 * to a real player in that game.
 *
 * Once connected, a client can send `{"type":"ready"}` to mark itself ready
 * (once every player in the game has, turn order is randomly decided and the
 * game starts) and `{"type":"take_turn"}` on its turn to move one space.
 * See GameSimulationService for the actual rules.
 *
 * Session bookkeeping (who's connected to which game) lives in memory on
 * this instance -- unlike the rest of this class, which just delegates to
 * PlayerDao/GameSimulationService, this part genuinely only works for a
 * single server instance: broadcasting an event only reaches sessions
 * connected to *this* instance. Fine for a small simulation; would need
 * something like Postgres LISTEN/NOTIFY or Redis pub/sub to work across
 * replicas.
 */
@Component
class GameWebSocketHandler(
	private val playerDao: PlayerDao,
	private val gameSimulationService: GameSimulationService,
	private val objectMapper: ObjectMapper,
) : TextWebSocketHandler() {

	private val connectionsBySession = ConcurrentHashMap<WebSocketSession, Connection>()
	private val sessionsByGame = ConcurrentHashMap<Uuid, MutableSet<WebSocketSession>>()

	override fun afterConnectionEstablished(session: WebSocketSession) {
		val params = UriComponentsBuilder.fromUri(session.uri ?: return session.reject("Missing connection URI.")).build().queryParams
		val gameId = params.getFirst("gameId")?.let { Uuid.parseOrNull(it) }
		val playerId = params.getFirst("playerId")?.let { Uuid.parseOrNull(it) }
		if (gameId == null || playerId == null) {
			return session.reject("gameId and playerId query params are required and must be valid ids.")
		}

		val player = playerDao.findById(playerId)
		if (player == null || player.gameId.value != gameId) {
			return session.reject("$playerId is not a player in game $gameId.")
		}

		connectionsBySession[session] = Connection(gameId, playerId)
		sessionsByGame.computeIfAbsent(gameId) { Collections.newSetFromMap(ConcurrentHashMap()) }.add(session)

		send(session, ConnectedEvent(playerId = playerId.toString()))
	}

	override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
		val connection = connectionsBySession.remove(session) ?: return
		sessionsByGame[connection.gameId]?.remove(session)
	}

	override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
		val connection = connectionsBySession[session] ?: return
		val type = runCatching { objectMapper.readValue(message.payload, ClientMessage::class.java) }.getOrNull()?.type

		when (type) {
			ClientMessageType.READY -> handleReady(session, connection)
			ClientMessageType.TAKE_TURN -> handleTakeTurn(session, connection)
			else -> send(session, ErrorEvent("Unrecognized message: ${message.payload}"))
		}
	}

	private fun handleReady(session: WebSocketSession, connection: Connection) {
		gameSimulationService.markReady(connection.gameId, connection.playerId).fold(
			onSuccess = { outcome ->
				broadcast(connection.gameId, PlayerReadyEvent(playerId = connection.playerId.toString()))
				if (outcome is GameSimulationService.ReadyOutcome.GameStarted) {
					broadcast(connection.gameId, GameStartedEvent(turnOrder = outcome.turnOrder.map { it.toString() }))
				}
			},
			onFailure = { error -> send(session, ErrorEvent(error.message ?: "Unable to mark ready.")) },
		)
	}

	private fun handleTakeTurn(session: WebSocketSession, connection: Connection) {
		gameSimulationService.takeTurn(connection.gameId, connection.playerId).fold(
			onSuccess = { turn ->
				broadcast(
					connection.gameId,
					TurnTakenEvent(
						turnNumber = turn.turnNumber,
						playerId = turn.playerId.toString(),
						fromSpaceId = turn.fromSpaceId?.toString(),
						toSpaceId = turn.toSpaceId.toString(),
					),
				)
				if (turn.gameOver) {
					broadcast(connection.gameId, GameOverEvent(turnCount = turn.turnNumber + 1))
				}
			},
			onFailure = { error -> send(session, ErrorEvent(error.message ?: "Unable to take turn.")) },
		)
	}

	private fun broadcast(gameId: Uuid, event: GameEvent) {
		sessionsByGame[gameId].orEmpty().forEach { send(it, event) }
	}

	private fun send(session: WebSocketSession, event: GameEvent) {
		if (!session.isOpen) return
		session.sendMessage(TextMessage(objectMapper.writeValueAsString(event)))
	}

	private fun WebSocketSession.reject(reason: String) {
		close(CloseStatus.BAD_DATA.withReason(reason.take(MAX_CLOSE_REASON_LENGTH)))
	}

	companion object {
		// WebSocket close reasons are limited to 123 UTF-8 bytes by the spec.
		private const val MAX_CLOSE_REASON_LENGTH = 100
	}
}
