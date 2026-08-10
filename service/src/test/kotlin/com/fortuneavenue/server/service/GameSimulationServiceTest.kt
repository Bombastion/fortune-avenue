package com.fortuneavenue.server.service

import com.fortuneavenue.server.dao.BoardDao
import com.fortuneavenue.server.dao.GameDao
import com.fortuneavenue.server.dao.PlayerDao
import com.fortuneavenue.server.models.board.db.Board
import com.fortuneavenue.server.models.board.db.BoardGraph
import com.fortuneavenue.server.models.board.db.BoardPath
import com.fortuneavenue.server.models.board.db.BoardSpacesTable
import com.fortuneavenue.server.models.board.db.BoardsTable
import com.fortuneavenue.server.models.game.db.Game
import com.fortuneavenue.server.models.player.db.Player
import com.fortuneavenue.server.models.player.db.PlayerState
import com.fortuneavenue.server.models.player.db.PlayerStatus
import com.fortuneavenue.server.models.player.db.PlayersTable
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import kotlin.uuid.Uuid

/**
 * Every mockFoo() helper below performs its own given().willReturn() calls.
 * Because of that, a helper call can NEVER be nested directly inside the
 * argument list of another given(...).willReturn(...) -- Kotlin evaluates
 * that argument (running the helper's own stubbing) before the outer
 * willReturn() is reached, and Mockito rejects starting a new stub while the
 * outer one is still open with an UnfinishedStubbingException. So every mock
 * gets built into a local val on its own line first, and only plain,
 * already-built values are ever passed to willReturn().
 *
 * The mockFoo() helpers stub every property a mock might need across *all*
 * tests, but any given test typically only exercises a subset of them (e.g.
 * markReady never touches game.boardId/turnNumber/maxTurns). With strict
 * stubbing (the MockitoExtension default) that leftover stubbing throws
 * UnnecessaryStubbingException, so helper stubs are set up via lenient()
 * rather than given(). Stubs set up directly in each test body stay strict,
 * since those are always expected to be used by that specific test.
 */
@ExtendWith(MockitoExtension::class)
class GameSimulationServiceTest {

	@Mock
	lateinit var gameDao: GameDao

	@Mock
	lateinit var playerDao: PlayerDao

	@Mock
	lateinit var boardDao: BoardDao

	private lateinit var service: GameSimulationService

	private val gameId = Uuid.random()
	private val boardId = Uuid.random()

	@BeforeEach
	fun setUp() {
		service = GameSimulationService(gameDao, playerDao, boardDao)
	}

	private fun mockPlayer(id: Uuid): Player {
		val player = mock(Player::class.java)
		lenient().`when`(player.id).thenReturn(EntityID(id, PlayersTable))
		return player
	}

	private fun mockPlayerState(status: PlayerStatus, currentSpaceId: Uuid? = null): PlayerState {
		val state = mock(PlayerState::class.java)
		lenient().`when`(state.status).thenReturn(status)
		lenient().`when`(state.currentSpaceId).thenReturn(currentSpaceId?.let { EntityID(it, BoardSpacesTable) })
		return state
	}

	private fun mockPath(from: Uuid, to: Uuid, branchOrder: Int): BoardPath {
		val path = mock(BoardPath::class.java)
		lenient().`when`(path.fromSpaceId).thenReturn(EntityID(from, BoardSpacesTable))
		lenient().`when`(path.toSpaceId).thenReturn(EntityID(to, BoardSpacesTable))
		lenient().`when`(path.branchOrder).thenReturn(branchOrder)
		return path
	}

	private fun mockBoard(startSpaceId: Uuid? = null): Board {
		val board = mock(Board::class.java)
		lenient().`when`(board.startSpaceId).thenReturn(startSpaceId)
		return board
	}

	private fun mockGame(turnOrder: List<Uuid>? = null, turnNumber: Int = 0, maxTurns: Int = 10): Game {
		val game = mock(Game::class.java)
		lenient().`when`(game.boardId).thenReturn(EntityID(boardId, BoardsTable))
		lenient().`when`(game.turnOrder).thenReturn(turnOrder)
		lenient().`when`(game.turnNumber).thenReturn(turnNumber)
		lenient().`when`(game.maxTurns).thenReturn(maxTurns)
		return game
	}

	// --- markReady ---

	@Test
	fun `markReady fails when the game doesn't exist`() {
		given(gameDao.findById(gameId)).willReturn(null)

		val result = service.markReady(gameId, Uuid.random())

		assertThat(result.exceptionOrNull()).isInstanceOf(GameNotFoundException::class.java)
	}

	@Test
	fun `markReady fails when the player isn't in the game`() {
		val game = mockGame()
		val otherPlayer = mockPlayer(Uuid.random())
		given(gameDao.findById(gameId)).willReturn(game)
		given(playerDao.findByGameId(gameId)).willReturn(listOf(otherPlayer))

		val result = service.markReady(gameId, Uuid.random())

		assertThat(result.exceptionOrNull()).isInstanceOf(InvalidPlayerException::class.java)
	}

	@Test
	fun `markReady marks the player ready and waits when not everyone is ready yet`() {
		val playerId = Uuid.random()
		val otherPlayerId = Uuid.random()
		val game = mockGame()
		val player = mockPlayer(playerId)
		val otherPlayer = mockPlayer(otherPlayerId)
		val otherPlayerState = mockPlayerState(PlayerStatus.WAITING)
		given(gameDao.findById(gameId)).willReturn(game)
		given(playerDao.findByGameId(gameId)).willReturn(listOf(player, otherPlayer))
		given(playerDao.findState(otherPlayerId)).willReturn(otherPlayerState)

		val result = service.markReady(gameId, playerId)

		assertThat(result.getOrNull()).isEqualTo(GameSimulationService.ReadyOutcome.Waiting)
		verify(playerDao).updateStatus(playerId, PlayerStatus.READY)
	}

	@Test
	fun `markReady starts the game once every player is ready`() {
		val playerId = Uuid.random()
		val otherPlayerId = Uuid.random()
		val game = mockGame()
		val player = mockPlayer(playerId)
		val otherPlayer = mockPlayer(otherPlayerId)
		val otherPlayerState = mockPlayerState(PlayerStatus.READY)
		given(gameDao.findById(gameId)).willReturn(game)
		given(playerDao.findByGameId(gameId)).willReturn(listOf(player, otherPlayer))
		given(playerDao.findState(otherPlayerId)).willReturn(otherPlayerState)

		val result = service.markReady(gameId, playerId)

		val outcome = result.getOrNull()
		assertThat(outcome).isInstanceOf(GameSimulationService.ReadyOutcome.GameStarted::class.java)
		val turnOrder = (outcome as GameSimulationService.ReadyOutcome.GameStarted).turnOrder
		assertThat(turnOrder).containsExactlyInAnyOrder(playerId, otherPlayerId)
		verify(gameDao).startGame(gameId, turnOrder)
	}

	@Test
	fun `markReady does not re-decide turn order once the game has already started`() {
		val playerId = Uuid.random()
		val game = mockGame(turnOrder = listOf(playerId))
		val player = mockPlayer(playerId)
		given(gameDao.findById(gameId)).willReturn(game)
		given(playerDao.findByGameId(gameId)).willReturn(listOf(player))

		val result = service.markReady(gameId, playerId)

		assertThat(result.getOrNull()).isEqualTo(GameSimulationService.ReadyOutcome.Waiting)
	}

	// --- takeTurn ---

	@Test
	fun `takeTurn fails when the game doesn't exist`() {
		given(gameDao.findById(gameId)).willReturn(null)

		val result = service.takeTurn(gameId, Uuid.random())

		assertThat(result.exceptionOrNull()).isInstanceOf(GameNotFoundException::class.java)
	}

	@Test
	fun `takeTurn fails when the game hasn't started`() {
		val game = mockGame(turnOrder = null)
		given(gameDao.findById(gameId)).willReturn(game)

		val result = service.takeTurn(gameId, Uuid.random())

		assertThat(result.exceptionOrNull()).isInstanceOf(InvalidTurnException::class.java)
	}

	@Test
	fun `takeTurn fails when the game is already over`() {
		val playerId = Uuid.random()
		val game = mockGame(turnOrder = listOf(playerId), turnNumber = 10, maxTurns = 10)
		given(gameDao.findById(gameId)).willReturn(game)

		val result = service.takeTurn(gameId, playerId)

		assertThat(result.exceptionOrNull()).isInstanceOf(InvalidTurnException::class.java)
	}

	@Test
	fun `takeTurn fails when it isn't the given player's turn`() {
		val currentPlayerId = Uuid.random()
		val otherPlayerId = Uuid.random()
		val game = mockGame(turnOrder = listOf(currentPlayerId, otherPlayerId))
		given(gameDao.findById(gameId)).willReturn(game)

		val result = service.takeTurn(gameId, otherPlayerId)

		assertThat(result.exceptionOrNull()).isInstanceOf(InvalidTurnException::class.java)
	}

	@Test
	fun `takeTurn moves a player from the board's start space on their first move`() {
		val playerId = Uuid.random()
		val startSpaceId = Uuid.random()
		val nextSpaceId = Uuid.random()
		val game = mockGame(turnOrder = listOf(playerId), turnNumber = 0, maxTurns = 10)
		val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = null)
		val board = mockBoard(startSpaceId = startSpaceId)
		val path = mockPath(from = startSpaceId, to = nextSpaceId, branchOrder = 0)
		val boardGraph = BoardGraph(board = board, spaces = emptyList(), paths = listOf(path))
		val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1, maxTurns = 10)
		given(gameDao.findById(gameId)).willReturn(game)
		given(playerDao.findState(playerId)).willReturn(playerState)
		given(boardDao.findById(boardId)).willReturn(boardGraph)
		given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

		val result = service.takeTurn(gameId, playerId)

		val turn = result.getOrNull()
		assertThat(turn).isNotNull()
		assertThat(turn!!.turnNumber).isEqualTo(0)
		assertThat(turn.fromSpaceId).isEqualTo(startSpaceId)
		assertThat(turn.toSpaceId).isEqualTo(nextSpaceId)
		assertThat(turn.gameOver).isFalse()
		verify(playerDao).updatePosition(playerId, nextSpaceId)
	}

	@Test
	fun `takeTurn picks the lowest branchOrder when a space has more than one outgoing path`() {
		val playerId = Uuid.random()
		val currentSpaceId = Uuid.random()
		val wrongBranch = Uuid.random()
		val rightBranch = Uuid.random()
		val game = mockGame(turnOrder = listOf(playerId))
		val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = currentSpaceId)
		val board = mockBoard()
		val higherBranchPath = mockPath(from = currentSpaceId, to = wrongBranch, branchOrder = 1)
		val lowerBranchPath = mockPath(from = currentSpaceId, to = rightBranch, branchOrder = 0)
		val boardGraph = BoardGraph(board = board, spaces = emptyList(), paths = listOf(higherBranchPath, lowerBranchPath))
		val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1)
		given(gameDao.findById(gameId)).willReturn(game)
		given(playerDao.findState(playerId)).willReturn(playerState)
		given(boardDao.findById(boardId)).willReturn(boardGraph)
		given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

		val result = service.takeTurn(gameId, playerId)

		assertThat(result.getOrNull()?.toSpaceId).isEqualTo(rightBranch)
	}

	@Test
	fun `takeTurn reports the game as over once it reaches max turns`() {
		val playerId = Uuid.random()
		val spaceId = Uuid.random()
		val nextSpaceId = Uuid.random()
		val game = mockGame(turnOrder = listOf(playerId), turnNumber = 9, maxTurns = 10)
		val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceId)
		val board = mockBoard()
		val path = mockPath(from = spaceId, to = nextSpaceId, branchOrder = 0)
		val boardGraph = BoardGraph(board = board, spaces = emptyList(), paths = listOf(path))
		val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 10, maxTurns = 10)
		given(gameDao.findById(gameId)).willReturn(game)
		given(playerDao.findState(playerId)).willReturn(playerState)
		given(boardDao.findById(boardId)).willReturn(boardGraph)
		given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

		val result = service.takeTurn(gameId, playerId)

		assertThat(result.getOrNull()?.gameOver).isTrue()
	}

	@Test
	fun `takeTurn fails when there's no path forward from the current space`() {
		val playerId = Uuid.random()
		val spaceId = Uuid.random()
		val game = mockGame(turnOrder = listOf(playerId))
		val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceId)
		val board = mockBoard()
		val boardGraph = BoardGraph(board = board, spaces = emptyList(), paths = emptyList())
		given(gameDao.findById(gameId)).willReturn(game)
		given(playerDao.findState(playerId)).willReturn(playerState)
		given(boardDao.findById(boardId)).willReturn(boardGraph)

		val result = service.takeTurn(gameId, playerId)

		assertThat(result.exceptionOrNull()).isInstanceOf(InvalidTurnException::class.java)
	}
}
