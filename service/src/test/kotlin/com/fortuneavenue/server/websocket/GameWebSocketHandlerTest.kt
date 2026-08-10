package com.fortuneavenue.server.websocket

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fortuneavenue.server.models.board.db.SpaceType
import com.fortuneavenue.server.models.board.rest.BoardResponse
import com.fortuneavenue.server.models.board.rest.CreateBoardPathRequest
import com.fortuneavenue.server.models.board.rest.CreateBoardRequest
import com.fortuneavenue.server.models.board.rest.CreateBoardSpaceRequest
import com.fortuneavenue.server.models.game.rest.CreateGameRequest
import com.fortuneavenue.server.models.game.rest.GameResponse
import com.fortuneavenue.server.models.player.rest.AddPlayerRequest
import com.fortuneavenue.server.models.player.rest.PlayerResponse
import com.fortuneavenue.server.models.user.rest.CreateUserRequest
import com.fortuneavenue.server.models.user.rest.UserResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.postForEntity
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid

@SpringBootTest(
	webEnvironment = WebEnvironment.DEFINED_PORT,
	properties = ["server.port=18099"],
)
class GameWebSocketHandlerTest {

	@Autowired
	lateinit var restTemplate: TestRestTemplate

	@Autowired
	lateinit var objectMapper: ObjectMapper

	/** A tiny WS test client: queues every received event and remembers how the connection closed, if it did. */
	private inner class RecordingClient {
		private val events = LinkedBlockingQueue<JsonNode>()
		private val closed = CompletableFuture<CloseStatus>()
		private lateinit var session: WebSocketSession

		fun connect(gameId: String, playerId: String) {
			val handler = object : TextWebSocketHandler() {
				override fun handleTextMessage(webSocketSession: WebSocketSession, message: TextMessage) {
					events.add(objectMapper.readTree(message.payload))
				}

				override fun afterConnectionClosed(webSocketSession: WebSocketSession, status: CloseStatus) {
					closed.complete(status)
				}
			}
			session = StandardWebSocketClient().execute(
				handler,
				WebSocketHttpHeaders(),
				URI("ws://localhost:$WS_TEST_PORT/ws/game?gameId=$gameId&playerId=$playerId"),
			).get(5, TimeUnit.SECONDS)
		}

		fun send(type: String) {
			session.sendMessage(TextMessage(objectMapper.writeValueAsString(ClientMessage(type))))
		}

		fun nextEvent(): JsonNode = events.poll(5, TimeUnit.SECONDS) ?: error("Timed out waiting for an event.")

		fun closeStatus(): CloseStatus = closed.get(5, TimeUnit.SECONDS)
	}

	private fun createBoard(): BoardResponse = restTemplate.postForEntity<BoardResponse>(
		"/boards",
		CreateBoardRequest(
			name = "loop-${Uuid.random()}",
			spaces = listOf(
				CreateBoardSpaceRequest(SpaceType.BASIC),
				CreateBoardSpaceRequest(SpaceType.BASIC),
				CreateBoardSpaceRequest(SpaceType.BASIC),
			),
			paths = listOf(
				CreateBoardPathRequest(0, 1),
				CreateBoardPathRequest(1, 2),
				CreateBoardPathRequest(2, 0),
			),
			startSpaceIndex = 0,
		),
	).body!!

	private fun createGame(): GameResponse =
		restTemplate.postForEntity<GameResponse>("/games", CreateGameRequest(boardId = createBoard().id)).body!!

	private fun createUser(): UserResponse =
		restTemplate.postForEntity<UserResponse>("/users", CreateUserRequest(username = "user-${Uuid.random()}")).body!!

	/**
	 * Omitting [userId] adds a computer player (no user behind the seat) --
	 * pass a real user's id for a human one. Most of these tests want an
	 * actual person driving ready-up/take-turn over the socket, so they pass
	 * one; the computer-player test below deliberately doesn't.
	 */
	private fun addPlayer(gameId: String, userId: String? = null): PlayerResponse =
		restTemplate.postForEntity<PlayerResponse>("/games/$gameId/players", AddPlayerRequest(userId = userId)).body!!

	private fun addHumanPlayer(gameId: String): PlayerResponse = addPlayer(gameId, userId = createUser().id)

	@Test
	fun `connecting as a real player in a real game sends a connected event`() {
		val game = createGame()
		val player = addPlayer(game.id)
		val client = RecordingClient()

		client.connect(game.id, player.id)

		val event = client.nextEvent()
		assertThat(event["type"].asText()).isEqualTo("connected")
		assertThat(event["playerId"].asText()).isEqualTo(player.id)
	}

	@Test
	fun `connecting with a player id that isn't in the game is rejected`() {
		val game = createGame()
		val otherGamesPlayer = addPlayer(createGame().id)
		val client = RecordingClient()

		client.connect(game.id, otherGamesPlayer.id)

		assertThat(client.closeStatus().code).isEqualTo(CloseStatus.BAD_DATA.code)
	}

	@Test
	fun `connecting with a malformed id is rejected`() {
		val client = RecordingClient()

		client.connect(gameId = "not-a-uuid", playerId = "also-not-a-uuid")

		assertThat(client.closeStatus().code).isEqualTo(CloseStatus.BAD_DATA.code)
	}

	@Test
	fun `two players readying up starts the game, and the first player in turn order can move`() {
		val game = createGame()
		val playerA = addHumanPlayer(game.id)
		val playerB = addHumanPlayer(game.id)
		val clientA = RecordingClient().also { it.connect(game.id, playerA.id) }
		val clientB = RecordingClient().also { it.connect(game.id, playerB.id) }
		assertThat(clientA.nextEvent()["type"].asText()).isEqualTo("connected")
		assertThat(clientB.nextEvent()["type"].asText()).isEqualTo("connected")

		clientA.send("ready")
		// Both clients see A ready up (order between the two isn't guaranteed, so just consume one each).
		assertThat(clientA.nextEvent()["type"].asText()).isEqualTo("player_ready")
		assertThat(clientB.nextEvent()["type"].asText()).isEqualTo("player_ready")

		clientB.send("ready")
		assertThat(clientA.nextEvent()["type"].asText()).isEqualTo("player_ready")
		assertThat(clientB.nextEvent()["type"].asText()).isEqualTo("player_ready")
		val startedEventA = clientA.nextEvent()
		val startedEventB = clientB.nextEvent()
		assertThat(startedEventA["type"].asText()).isEqualTo("game_started")
		assertThat(startedEventB).isEqualTo(startedEventA)
		val turnOrder = startedEventA["turnOrder"].map { it.asText() }
		assertThat(turnOrder).containsExactlyInAnyOrder(playerA.id, playerB.id)

		val firstClient = if (turnOrder.first() == playerA.id) clientA else clientB
		val secondClient = if (firstClient === clientA) clientB else clientA

		secondClient.send("take_turn")
		assertThat(secondClient.nextEvent()["type"].asText()).isEqualTo("error")

		firstClient.send("take_turn")
		val turnEventOnFirst = firstClient.nextEvent()
		assertThat(turnEventOnFirst["type"].asText()).isEqualTo("turn_taken")
		assertThat(turnEventOnFirst["playerId"].asText()).isEqualTo(turnOrder.first())
		assertThat(turnEventOnFirst["turnNumber"].asInt()).isEqualTo(0)
		assertThat(secondClient.nextEvent()).isEqualTo(turnEventOnFirst)
	}

	@Test
	fun `a computer player is automatically readied once every human player is`() {
		val game = createGame()
		val human = addHumanPlayer(game.id)
		addPlayer(game.id) // no userId -- a computer player
		val client = RecordingClient().also { it.connect(game.id, human.id) }
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("connected")

		client.send("ready")

		// Only the human ever sends "ready" -- the computer player getting
		// readied up automatically is what lets the game start right away.
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("player_ready")
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("game_started")
	}

	companion object {
		private const val WS_TEST_PORT = 18099
	}
}
