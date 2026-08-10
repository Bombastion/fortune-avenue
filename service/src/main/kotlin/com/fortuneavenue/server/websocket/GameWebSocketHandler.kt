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
 * (once every human player in the game has, computer players are readied up
 * automatically and turn order is randomly decided as the game starts -- if
 * that puts one or more computer players first, their turns get played out
 * and broadcast immediately, right after the game_started event) and
 * `{"type":"roll_dice"}` on its turn to roll and move forward that many
 * spaces. If that movement reaches a space with more than one path out of
 * it, it pauses there and a `choice_required` event lists the options --
 * respond with `{"type":"choose_path","spaceId":"<id>"}` to pick one and
 * keep moving. Any computer players whose turns immediately follow (once
 * the current player's turn actually ends) are played out automatically
 * too, each broadcast in turn order right after the requested one.
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
		val clientMessage = runCatching { objectMapper.readValue(message.payload, ClientMessage::class.java) }.getOrNull()

		when (clientMessage?.type) {
			ClientMessageType.READY -> handleReady(session, connection)
			ClientMessageType.ROLL_DICE -> handleRollDice(session, connection)
			ClientMessageType.CHOOSE_PATH -> handleChoosePath(session, connection, clientMessage?.spaceId)
			else -> send(session, ErrorEvent("Unrecognized message: ${message.payload}"))
		}
	}

	private fun handleReady(session: WebSocketSession, connection: Connection) {
		gameSimulationService.markReady(connection.gameId, connection.playerId).fold(
			onSuccess = { outcome ->
				broadcast(connection.gameId, PlayerReadyEvent(playerId = connection.playerId.toString()))
				if (outcome is GameSimulationService.ReadyOutcome.GameStarted) {
					broadcast(connection.gameId, GameStartedEvent(turnOrder = outcome.turnOrder.map { it.toString() }))
					// If turn order came out with one or more computer players
					// first, their turns were already played -- broadcast those
					// too so clients see the board update without anyone having
					// to roll the dice on their behalf.
					outcome.openingTurnEvents.forEach { event -> broadcastTurnEvent(connection.gameId, event) }
				}
			},
			onFailure = { error -> send(session, ErrorEvent(error.message ?: "Unable to mark ready.")) },
		)
	}

	private fun handleRollDice(session: WebSocketSession, connection: Connection) {
		gameSimulationService.rollDice(connection.gameId, connection.playerId).fold(
			onSuccess = { events ->
				// [events] is the roll, any moves and/or choice pause it
				// caused, and then any computer players' full turns that got
				// auto-played right after -- each gets broadcast in order,
				// same as if every one of them had been requested individually.
				events.forEach { event -> broadcastTurnEvent(connection.gameId, event) }
			},
			onFailure = { error -> send(session, ErrorEvent(error.message ?: "Unable to roll the dice.")) },
		)
	}

	private fun handleChoosePath(session: WebSocketSession, connection: Connection, spaceId: String?) {
		val toSpaceId = spaceId?.let { Uuid.parseOrNull(it) }
			?: return send(session, ErrorEvent("choose_path requires a valid spaceId."))

		gameSimulationService.choosePath(connection.gameId, connection.playerId, toSpaceId).fold(
			onSuccess = { events -> events.forEach { event -> broadcastTurnEvent(connection.gameId, event) } },
			onFailure = { error -> send(session, ErrorEvent(error.message ?: "Unable to choose a path.")) },
		)
	}

	private fun broadcastTurnEvent(gameId: Uuid, event: GameSimulationService.TurnEvent) {
		broadcast(gameId, event.toWireEvent())
		if (event is GameSimulationService.TurnEvent.TurnEnded && event.gameOver) {
			broadcast(gameId, GameOverEvent(turnCount = event.turnNumber + 1))
		}
	}

	private fun GameSimulationService.TurnEvent.toWireEvent(): GameEvent = when (this) {
		is GameSimulationService.TurnEvent.DiceRolled -> DiceRolledEvent(playerId = playerId.toString(), roll = roll)
		is GameSimulationService.TurnEvent.Moved -> PlayerMovedEvent(
			turnNumber = turnNumber,
			playerId = playerId.toString(),
			fromSpaceId = fromSpaceId.toString(),
			toSpaceId = toSpaceId.toString(),
			movementPointsRemaining = movementPointsRemaining,
		)
		is GameSimulationService.TurnEvent.ChoiceRequired -> ChoiceRequiredEvent(
			playerId = playerId.toString(),
			spaceId = spaceId.toString(),
			options = options.map { PathOptionPayload(it.toSpaceId.toString(), it.branchOrder) },
		)
		is GameSimulationService.TurnEvent.TurnEnded -> TurnEndedEvent(turnNumber = turnNumber, playerId = playerId.toString())
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
