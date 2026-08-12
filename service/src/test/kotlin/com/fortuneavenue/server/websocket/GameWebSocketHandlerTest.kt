package com.fortuneavenue.server.websocket

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fortuneavenue.server.DatabaseTest
import com.fortuneavenue.server.dao.GameShopInformationDao
import com.fortuneavenue.server.dao.PlayerDao
import com.fortuneavenue.server.models.board.db.SpaceType
import com.fortuneavenue.server.models.board.rest.BoardResponse
import com.fortuneavenue.server.models.board.rest.CreateBoardPathRequest
import com.fortuneavenue.server.models.board.rest.CreateBoardRequest
import com.fortuneavenue.server.models.board.rest.CreateBoardSpaceRequest
import com.fortuneavenue.server.models.board.rest.CreateDistrictProgressionRequest
import com.fortuneavenue.server.models.board.rest.CreateDistrictRequest
import com.fortuneavenue.server.models.game.rest.CreateGameRequest
import com.fortuneavenue.server.models.game.rest.GameResponse
import com.fortuneavenue.server.models.player.rest.AddPlayerRequest
import com.fortuneavenue.server.models.player.rest.PlayerResponse
import com.fortuneavenue.server.models.user.rest.CreateUserRequest
import com.fortuneavenue.server.models.user.rest.UserResponse
import com.fortuneavenue.server.service.Dice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.postForEntity
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.math.BigDecimal
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid

@SpringBootTest(
	webEnvironment = WebEnvironment.DEFINED_PORT,
	properties = ["server.port=18099"],
)
class GameWebSocketHandlerTest : DatabaseTest() {

	@Autowired
	lateinit var restTemplate: TestRestTemplate

	@Autowired
	lateinit var objectMapper: ObjectMapper

	// The real RandomDice bean is replaced by QueuedDice below (see DiceTestConfig) for every test
	// in this class -- shop-purchase scenarios need a roll that lands exactly on a given space,
	// which an actual random 1-6 can't reliably promise. Autowired as the interface since that's
	// all production code ever depends on; cast down to QueuedDice to enqueue rolls per test.
	@Autowired
	lateinit var dice: Dice

	// Not exposed over the wire anywhere (no ShopController/gold field on PlayerResponse -- see
	// GameShopInformationDao/PlayerDao), so purchase side effects (gold deducted, ownership set,
	// district values recalculated) are verified by querying these DAOs directly after the fact,
	// same as any @SpringBootTest DAO test would.
	@Autowired
	lateinit var playerDao: PlayerDao

	@Autowired
	lateinit var gameShopInformationDao: GameShopInformationDao

	@BeforeEach
	fun resetDice() {
		(dice as QueuedDice).clear()
	}

	/**
	 * Deterministic stand-in for RandomDice -- see [DiceTestConfig], which registers this as the
	 * @Primary Dice bean for this whole test class. Matches exactly what the doc comment on [Dice]
	 * says the interface is for: tests enqueue exactly the rolls a scenario needs, one per
	 * roll_dice call (including any consumed by an auto-played computer turn), and pop them off in
	 * order. The Spring context (and so this bean) is shared across every test in the class, so
	 * [resetDice] clears any of the previous test's leftovers before each one runs.
	 */
	private class QueuedDice : Dice {
		private val queue = ConcurrentLinkedQueue<Int>()

		fun enqueue(vararg rolls: Int) {
			queue.addAll(rolls.toList())
		}

		fun clear() = queue.clear()

		override fun roll(): Int =
			queue.poll() ?: error("QueuedDice ran out of queued rolls -- enqueue one for every roll this scenario triggers.")
	}

	@TestConfiguration
	class DiceTestConfig {
		@Bean
		@Primary
		fun dice(): Dice = QueuedDice()
	}

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

		fun send(type: String, spaceId: String? = null) {
			session.sendMessage(TextMessage(objectMapper.writeValueAsString(ClientMessage(type, spaceId))))
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
			startingGold = 1000,
		),
	).body!!

	/**
	 * Space 0 (the start) forks to spaces 1 and 2, each of which leads
	 * straight back to 0 -- so movement always pauses for a choice the
	 * instant it reaches (or returns to) the start, regardless of the roll.
	 */
	private fun createBranchingBoard(): BoardResponse = restTemplate.postForEntity<BoardResponse>(
		"/boards",
		CreateBoardRequest(
			name = "branch-${Uuid.random()}",
			spaces = listOf(
				CreateBoardSpaceRequest(SpaceType.BASIC),
				CreateBoardSpaceRequest(SpaceType.BASIC),
				CreateBoardSpaceRequest(SpaceType.BASIC),
			),
			paths = listOf(
				CreateBoardPathRequest(0, 1, branchOrder = 0),
				CreateBoardPathRequest(0, 2, branchOrder = 1),
				CreateBoardPathRequest(1, 0),
				CreateBoardPathRequest(2, 0),
			),
			startSpaceIndex = 0,
			startingGold = 1000,
		),
	).body!!

	private fun createGame(board: BoardResponse = createBoard()): GameResponse =
		restTemplate.postForEntity<GameResponse>("/games", CreateGameRequest(boardId = board.id)).body!!

	private fun createUser(): UserResponse =
		restTemplate.postForEntity<UserResponse>("/users", CreateUserRequest(username = "user-${Uuid.random()}")).body!!

	/**
	 * Omitting [userId] adds a computer player (no user behind the seat) --
	 * pass a real user's id for a human one. Most of these tests want an
	 * actual person driving ready-up/roll-dice over the socket, so they pass
	 * one; the computer-player test below deliberately doesn't.
	 */
	private fun addPlayer(gameId: String, userId: String? = null): PlayerResponse =
		restTemplate.postForEntity<PlayerResponse>("/games/$gameId/players", AddPlayerRequest(userId = userId)).body!!

	private fun addHumanPlayer(gameId: String): PlayerResponse = addPlayer(gameId, userId = createUser().id)

	/**
	 * A loop of two spaces: the start, and a single SHOP with only one path out of either --
	 * so a roll of 1 from the start always lands on the shop, with nothing to branch on.
	 */
	private fun createShopBoard(baseValue: Int = 300, startingGold: Int = 1000): BoardResponse = restTemplate.postForEntity<BoardResponse>(
		"/boards",
		CreateBoardRequest(
			name = "shop-${Uuid.random()}",
			spaces = listOf(
				CreateBoardSpaceRequest(SpaceType.BASIC),
				CreateBoardSpaceRequest(SpaceType.SHOP, baseValue = baseValue, basePricePercentage = BigDecimal("0.2500")),
			),
			paths = listOf(
				CreateBoardPathRequest(0, 1),
				CreateBoardPathRequest(1, 0),
			),
			startSpaceIndex = 0,
			startingGold = startingGold,
		),
	).body!!

	/**
	 * A loop of three spaces: the start, then two SHOP spaces in the same district, each with only
	 * one path out -- so a roll of 1 from the start always lands on the first shop, and a roll of 1
	 * from there always lands on the second, with nothing to branch on either time. The district
	 * defines a single progression level (ownedShopCount=2, its only valid level for a 2-space
	 * district), so buying the second shop while already owning the first triggers a recalculation.
	 */
	private fun createDistrictShopBoard(): BoardResponse = restTemplate.postForEntity<BoardResponse>(
		"/boards",
		CreateBoardRequest(
			name = "district-shop-${Uuid.random()}",
			spaces = listOf(
				CreateBoardSpaceRequest(SpaceType.BASIC),
				CreateBoardSpaceRequest(SpaceType.SHOP, baseValue = 100, basePricePercentage = BigDecimal("0.2500"), districtIndex = 0),
				CreateBoardSpaceRequest(SpaceType.SHOP, baseValue = 200, basePricePercentage = BigDecimal("0.2500"), districtIndex = 0),
			),
			paths = listOf(
				CreateBoardPathRequest(0, 1),
				CreateBoardPathRequest(1, 2),
				CreateBoardPathRequest(2, 0),
			),
			startSpaceIndex = 0,
			startingGold = 1000,
			districts = listOf(
				CreateDistrictRequest(
					name = "Red",
					colorHex = "FF0000",
					progressions = listOf(
						CreateDistrictProgressionRequest(
							ownedShopCount = 2,
							existingShopBoostPercentage = BigDecimal("0.1000"),
							newShopBoostPercentage = BigDecimal("0.2000"),
						),
					),
				),
			),
		),
	).body!!

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
	fun `two players readying up starts the game, and the first player in turn order can roll and move`() {
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

		// Neither player leading turn order is a computer, so the game
		// announces whose turn it is immediately, right after game_started.
		val turnStartedA = clientA.nextEvent()
		val turnStartedB = clientB.nextEvent()
		assertThat(turnStartedA["type"].asText()).isEqualTo("turn_started")
		assertThat(turnStartedA["playerId"].asText()).isEqualTo(turnOrder.first())
		assertThat(turnStartedB).isEqualTo(turnStartedA)

		val firstClient = if (turnOrder.first() == playerA.id) clientA else clientB
		val secondClient = if (firstClient === clientA) clientB else clientA

		secondClient.send("roll_dice")
		assertThat(secondClient.nextEvent()["type"].asText()).isEqualTo("error")

		// The specific value doesn't matter to this test -- it just asserts against whatever comes
		// back (isBetween/repeat below) -- but Dice is QueuedDice for this whole class now, so it
		// still needs something queued or roll_dice has nothing to roll.
		(dice as QueuedDice).enqueue(3)
		firstClient.send("roll_dice")
		val diceEventOnFirst = firstClient.nextEvent()
		assertThat(diceEventOnFirst["type"].asText()).isEqualTo("dice_rolled")
		assertThat(diceEventOnFirst["playerId"].asText()).isEqualTo(turnOrder.first())
		val roll = diceEventOnFirst["roll"].asInt()
		assertThat(roll).isBetween(1, 6)
		assertThat(secondClient.nextEvent()).isEqualTo(diceEventOnFirst)

		// The board is a plain loop with no branches, so the roll always
		// resolves as that many player_moved events in a row, then the turn
		// ending -- nothing ever pauses for a choice.
		repeat(roll) {
			val movedOnFirst = firstClient.nextEvent()
			assertThat(movedOnFirst["type"].asText()).isEqualTo("player_moved")
			assertThat(secondClient.nextEvent()).isEqualTo(movedOnFirst)
		}

		val turnEndedOnFirst = firstClient.nextEvent()
		assertThat(turnEndedOnFirst["type"].asText()).isEqualTo("turn_ended")
		assertThat(turnEndedOnFirst["turnNumber"].asInt()).isEqualTo(0)
		assertThat(secondClient.nextEvent()).isEqualTo(turnEndedOnFirst)
	}

	@Test
	fun `reaching a branch pauses for a choice, which choose_path then resolves`() {
		val game = createGame(createBranchingBoard())
		val playerA = addHumanPlayer(game.id)
		val playerB = addHumanPlayer(game.id)
		val clientA = RecordingClient().also { it.connect(game.id, playerA.id) }
		val clientB = RecordingClient().also { it.connect(game.id, playerB.id) }
		assertThat(clientA.nextEvent()["type"].asText()).isEqualTo("connected")
		assertThat(clientB.nextEvent()["type"].asText()).isEqualTo("connected")

		clientA.send("ready")
		clientA.nextEvent()
		clientB.nextEvent()
		clientB.send("ready")
		clientA.nextEvent()
		clientB.nextEvent()
		val startedEventA = clientA.nextEvent()
		clientB.nextEvent()
		// Neither player leading turn order is a computer, so the game
		// announces whose turn it is immediately, right after game_started.
		clientA.nextEvent()
		clientB.nextEvent()
		val turnOrder = startedEventA["turnOrder"].map { it.asText() }
		val firstClient = if (turnOrder.first() == playerA.id) clientA else clientB

		// The start space forks immediately, so movement pauses before the roll's value ever
		// matters -- but something still has to be queued for dice.roll() to return.
		(dice as QueuedDice).enqueue(1)
		firstClient.send("roll_dice")
		assertThat(firstClient.nextEvent()["type"].asText()).isEqualTo("dice_rolled")
		val choiceEvent = firstClient.nextEvent()
		assertThat(choiceEvent["type"].asText()).isEqualTo("choice_required")
		val options = choiceEvent["options"].map { it["toSpaceId"].asText() }
		assertThat(options).hasSize(2)

		firstClient.send("choose_path", spaceId = options.first())
		val movedEvent = firstClient.nextEvent()
		assertThat(movedEvent["type"].asText()).isEqualTo("player_moved")
		assertThat(movedEvent["toSpaceId"].asText()).isEqualTo(options.first())
	}

	@Test
	fun `a computer player is automatically readied once every human player is`() {
		val game = createGame()
		val human = addHumanPlayer(game.id)
		addPlayer(game.id) // no userId -- a computer player
		val client = RecordingClient().also { it.connect(game.id, human.id) }
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("connected")

		// Turn order is shuffled, so the computer might lead it -- in which case its whole turn
		// (one dice.roll() call) plays out immediately as part of the game_started broadcast below.
		// Queued but possibly unused if the human leads instead; either way nothing here depends on
		// its value, since the board's a plain loop with no branches to land on.
		(dice as QueuedDice).enqueue(1)
		client.send("ready")

		// Only the human ever sends "ready" -- the computer player getting
		// readied up automatically is what lets the game start right away.
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("player_ready")
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("game_started")
	}

	@Test
	fun `landing on an unowned shop pauses for a purchase, and buy_shop buys it`() {
		val board = createShopBoard(baseValue = 300)
		val game = createGame(board)
		val player = addHumanPlayer(game.id)
		val client = RecordingClient().also { it.connect(game.id, player.id) }
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("connected")

		client.send("ready")
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("player_ready")
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("game_started")
		// Sole player and human, so nothing else needs to happen before it's announced as their turn.
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("turn_started")

		(dice as QueuedDice).enqueue(1)
		client.send("roll_dice")
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("dice_rolled")
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("player_moved")
		val pauseEvent = client.nextEvent()
		assertThat(pauseEvent["type"].asText()).isEqualTo("shop_purchase_available")
		assertThat(pauseEvent["playerId"].asText()).isEqualTo(player.id)
		assertThat(pauseEvent["price"].asInt()).isEqualTo(300)
		val shopSpaceId = pauseEvent["spaceId"].asText()

		client.send("buy_shop")
		val purchasedEvent = client.nextEvent()
		assertThat(purchasedEvent["type"].asText()).isEqualTo("shop_purchased")
		assertThat(purchasedEvent["playerId"].asText()).isEqualTo(player.id)
		assertThat(purchasedEvent["spaceId"].asText()).isEqualTo(shopSpaceId)
		assertThat(purchasedEvent["price"].asInt()).isEqualTo(300)
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("turn_ended")
		// Sole player again, so their next turn is announced immediately.
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("turn_started")

		val playerId = Uuid.parse(player.id)
		assertThat(playerDao.findState(playerId)!!.currentGold).isEqualTo(board.startingGold - 300)
		val shop = gameShopInformationDao.findByGameAndSpace(Uuid.parse(game.id), Uuid.parse(shopSpaceId))
		assertThat(shop?.ownerId?.value).isEqualTo(playerId)
	}

	@Test
	fun `decline_shop ends the turn without buying, leaving the shop unowned and gold untouched`() {
		val board = createShopBoard(baseValue = 300)
		val game = createGame(board)
		val player = addHumanPlayer(game.id)
		val client = RecordingClient().also { it.connect(game.id, player.id) }
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("connected")

		client.send("ready")
		client.nextEvent() // player_ready
		client.nextEvent() // game_started
		client.nextEvent() // turn_started

		(dice as QueuedDice).enqueue(1)
		client.send("roll_dice")
		client.nextEvent() // dice_rolled
		client.nextEvent() // player_moved
		val pauseEvent = client.nextEvent()
		assertThat(pauseEvent["type"].asText()).isEqualTo("shop_purchase_available")
		val shopSpaceId = pauseEvent["spaceId"].asText()

		client.send("decline_shop")
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("turn_ended")
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("turn_started")

		val playerId = Uuid.parse(player.id)
		assertThat(playerDao.findState(playerId)!!.currentGold).isEqualTo(board.startingGold)
		val shop = gameShopInformationDao.findByGameAndSpace(Uuid.parse(game.id), Uuid.parse(shopSpaceId))
		assertThat(shop?.ownerId).isNull()
	}

	@Test
	fun `buy_shop fails when the player can't afford the price, leaving the decision pending`() {
		val board = createShopBoard(baseValue = 300, startingGold = 50)
		val game = createGame(board)
		val player = addHumanPlayer(game.id)
		val client = RecordingClient().also { it.connect(game.id, player.id) }
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("connected")

		client.send("ready")
		client.nextEvent() // player_ready
		client.nextEvent() // game_started
		client.nextEvent() // turn_started

		(dice as QueuedDice).enqueue(1)
		client.send("roll_dice")
		client.nextEvent() // dice_rolled
		client.nextEvent() // player_moved
		val pauseEvent = client.nextEvent()
		assertThat(pauseEvent["type"].asText()).isEqualTo("shop_purchase_available")
		val shopSpaceId = pauseEvent["spaceId"].asText()

		client.send("buy_shop")
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("error")

		// The decision is still pending -- declining now (rather than retrying buy_shop) is what
		// finally ends the turn.
		client.send("decline_shop")
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("turn_ended")
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("turn_started")

		val playerId = Uuid.parse(player.id)
		assertThat(playerDao.findState(playerId)!!.currentGold).isEqualTo(50)
		val shop = gameShopInformationDao.findByGameAndSpace(Uuid.parse(game.id), Uuid.parse(shopSpaceId))
		assertThat(shop?.ownerId).isNull()
	}

	@Test
	fun `buying a second shop in the same district recalculates every shop the player owns there`() {
		val board = createDistrictShopBoard()
		val game = createGame(board)
		val player = addHumanPlayer(game.id)
		val firstShopSpaceId = board.spaces[1].id
		val secondShopSpaceId = board.spaces[2].id
		val client = RecordingClient().also { it.connect(game.id, player.id) }
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("connected")

		client.send("ready")
		client.nextEvent() // player_ready
		client.nextEvent() // game_started
		client.nextEvent() // turn_started

		// Turn 1: land on and buy the first shop -- only one shop owned yet, nothing to recalculate.
		(dice as QueuedDice).enqueue(1)
		client.send("roll_dice")
		client.nextEvent() // dice_rolled
		val firstMove = client.nextEvent()
		assertThat(firstMove["toSpaceId"].asText()).isEqualTo(firstShopSpaceId)
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("shop_purchase_available")

		client.send("buy_shop")
		val firstPurchase = client.nextEvent()
		assertThat(firstPurchase["type"].asText()).isEqualTo("shop_purchased")
		assertThat(firstPurchase["spaceId"].asText()).isEqualTo(firstShopSpaceId)
		assertThat(firstPurchase["price"].asInt()).isEqualTo(100)
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("turn_ended")
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("turn_started")

		// Turn 2: land on and buy the second shop -- now owning 2 in the district, this recalculates both.
		(dice as QueuedDice).enqueue(1)
		client.send("roll_dice")
		client.nextEvent() // dice_rolled
		val secondMove = client.nextEvent()
		assertThat(secondMove["toSpaceId"].asText()).isEqualTo(secondShopSpaceId)
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("shop_purchase_available")

		client.send("buy_shop")
		val secondPurchase = client.nextEvent()
		assertThat(secondPurchase["type"].asText()).isEqualTo("shop_purchased")
		assertThat(secondPurchase["spaceId"].asText()).isEqualTo(secondShopSpaceId)
		assertThat(secondPurchase["price"].asInt()).isEqualTo(200)

		val recalculated = client.nextEvent()
		assertThat(recalculated["type"].asText()).isEqualTo("district_values_recalculated")
		assertThat(recalculated["districtId"].asText()).isEqualTo(board.districts.single().id)
		// existing shop (bought first, base 100) boosted by existingShopBoostPercentage (0.1000) -> 110
		assertThat(recalculated["newValuesBySpaceId"][firstShopSpaceId].asInt()).isEqualTo(110)
		// just-bought shop (base 200) boosted by newShopBoostPercentage (0.2000) -> 240
		assertThat(recalculated["newValuesBySpaceId"][secondShopSpaceId].asInt()).isEqualTo(240)

		assertThat(client.nextEvent()["type"].asText()).isEqualTo("turn_ended")
		assertThat(client.nextEvent()["type"].asText()).isEqualTo("turn_started")

		assertThat(gameShopInformationDao.findByGameAndSpace(Uuid.parse(game.id), Uuid.parse(firstShopSpaceId))?.currentValue)
			.isEqualTo(110)
		assertThat(gameShopInformationDao.findByGameAndSpace(Uuid.parse(game.id), Uuid.parse(secondShopSpaceId))?.currentValue)
			.isEqualTo(240)
		val playerId = Uuid.parse(player.id)
		assertThat(playerDao.findState(playerId)!!.currentGold).isEqualTo(board.startingGold - 100 - 200)
	}

	companion object {
		private const val WS_TEST_PORT = 18099
	}
}
