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
import com.fortuneavenue.server.models.user.db.UsersTable
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import kotlin.uuid.Uuid

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

	/** [userId] defaults to a human player -- pass null to mock a computer player instead. */
	private fun mockPlayer(id: Uuid, userId: Uuid? = Uuid.random()): Player {
		val player = mock(Player::class.java)
		lenient().`when`(player.id).thenReturn(EntityID(id, PlayersTable))
		lenient().`when`(player.userId).thenReturn(userId?.let { EntityID(it, UsersTable) })
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

	/** All orderings of a small, fixed list -- used to stub against every value a `shuffled()` call in production code could produce. */
	private fun <T> permutationsOf(items: List<T>): List<List<T>> = if (items.size <= 1) {
		listOf(items)
	} else {
		items.indices.flatMap { i ->
			val rest = items.toMutableList().apply { removeAt(i) }
			permutationsOf(rest).map { listOf(items[i]) + it }
		}
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

	@Test
	fun `markReady auto-readies computer players once every human player is ready`() {
		val humanId = Uuid.random()
		val computerId = Uuid.random()
		val game = mockGame()
		val human = mockPlayer(humanId)
		val computer = mockPlayer(computerId, userId = null)
		given(gameDao.findById(gameId)).willReturn(game)
		given(playerDao.findByGameId(gameId)).willReturn(listOf(human, computer))

		val result = service.markReady(gameId, humanId)

		val outcome = result.getOrNull()
		assertThat(outcome).isInstanceOf(GameSimulationService.ReadyOutcome.GameStarted::class.java)
		verify(playerDao).updateStatus(humanId, PlayerStatus.READY)
		verify(playerDao).updateStatus(computerId, PlayerStatus.READY)
	}

	@Test
	fun `markReady leaves computer players alone until every human player is ready`() {
		val humanId = Uuid.random()
		val otherHumanId = Uuid.random()
		val computerId = Uuid.random()
		val game = mockGame()
		val human = mockPlayer(humanId)
		val otherHuman = mockPlayer(otherHumanId)
		val computer = mockPlayer(computerId, userId = null)
		val otherHumanState = mockPlayerState(PlayerStatus.WAITING)
		given(gameDao.findById(gameId)).willReturn(game)
		given(playerDao.findByGameId(gameId)).willReturn(listOf(human, otherHuman, computer))
		given(playerDao.findState(otherHumanId)).willReturn(otherHumanState)

		val result = service.markReady(gameId, humanId)

		assertThat(result.getOrNull()).isEqualTo(GameSimulationService.ReadyOutcome.Waiting)
		verify(playerDao, never()).updateStatus(computerId, PlayerStatus.READY)
	}

	@Test
	fun `markReady plays every leading computer player's turn immediately once the game starts`() {
		// Turn order is randomly shuffled, so which (if any) of these two
		// computer players ends up leading it isn't known ahead of time --
		// the DAO stubs below thread turn state through their answers so the
		// mock keeps behaving correctly no matter what the real shuffle
		// picks, and the assertions check the outcome against whatever
		// order actually came back rather than assuming one.
		val humanId = Uuid.random()
		val computerAId = Uuid.random()
		val computerBId = Uuid.random()
		val startSpaceId = Uuid.random()
		val nextSpaceId = Uuid.random()
		val game = mockGame()
		val human = mockPlayer(humanId)
		val computerA = mockPlayer(computerAId, userId = null)
		val computerB = mockPlayer(computerBId, userId = null)
		val computerAState = mockPlayerState(PlayerStatus.READY, currentSpaceId = null)
		val computerBState = mockPlayerState(PlayerStatus.READY, currentSpaceId = null)
		val board = mockBoard(startSpaceId = startSpaceId)
		val path = mockPath(from = startSpaceId, to = nextSpaceId, branchOrder = 0)
		val boardGraph = BoardGraph(board = board, spaces = emptyList(), paths = listOf(path))
		given(gameDao.findById(gameId)).willReturn(game)
		given(playerDao.findByGameId(gameId)).willReturn(listOf(human, computerA, computerB))
		// Whether each of these actually gets used depends on where the real
		// shuffle put things (e.g. if the human leads, neither computer's
		// turn is ever played) -- lenient since strict stubbing would
		// otherwise randomly fail this test depending on shuffle outcome.
		lenient().`when`(playerDao.findState(computerAId)).thenReturn(computerAState)
		lenient().`when`(playerDao.findState(computerBId)).thenReturn(computerBState)
		lenient().`when`(boardDao.findById(boardId)).thenReturn(boardGraph)

		// gameDao.startGame() is called with whatever order shuffled() picks,
		// which isn't known ahead of time -- rather than reach for an any()
		// matcher (Mockito's any() returns null, which throws against this
		// non-null List<Uuid> parameter), stub every possible ordering of
		// these 3 players individually with a literal-value match. Only one
		// of the 6 will ever actually be hit per run, so they're lenient too.
		var decidedTurnOrder: List<Uuid>? = null
		var turnNumber = 0
		permutationsOf(listOf(humanId, computerAId, computerBId)).forEach { order ->
			lenient().`when`(gameDao.startGame(gameId, order)).thenAnswer {
				decidedTurnOrder = order
				mockGame(turnOrder = order, turnNumber = 0, maxTurns = 10)
			}
		}
		lenient().`when`(gameDao.advanceTurn(gameId)).thenAnswer {
			turnNumber += 1
			mockGame(turnOrder = decidedTurnOrder, turnNumber = turnNumber, maxTurns = 10)
		}

		val result = service.markReady(gameId, humanId)

		val outcome = result.getOrNull() as GameSimulationService.ReadyOutcome.GameStarted
		val leadingComputerIds = outcome.turnOrder.takeWhile { it != humanId }
		assertThat(outcome.openingComputerTurns.map { it.playerId }).isEqualTo(leadingComputerIds)
		outcome.openingComputerTurns.forEach { turn -> assertThat(turn.toSpaceId).isEqualTo(nextSpaceId) }
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

		val turns = result.getOrNull()
		assertThat(turns).hasSize(1)
		val turn = turns!!.single()
		assertThat(turn.turnNumber).isEqualTo(0)
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

		assertThat(result.getOrNull()?.single()?.toSpaceId).isEqualTo(rightBranch)
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

		assertThat(result.getOrNull()?.single()?.gameOver).isTrue()
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

	@Test
	fun `takeTurn does not auto-play the next human player's turn`() {
		val playerId = Uuid.random()
		val otherPlayerId = Uuid.random()
		val startSpaceId = Uuid.random()
		val nextSpaceId = Uuid.random()
		val game = mockGame(turnOrder = listOf(playerId, otherPlayerId), turnNumber = 0, maxTurns = 10)
		val player = mockPlayer(playerId)
		val otherPlayer = mockPlayer(otherPlayerId)
		val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = null)
		val board = mockBoard(startSpaceId = startSpaceId)
		val path = mockPath(from = startSpaceId, to = nextSpaceId, branchOrder = 0)
		val boardGraph = BoardGraph(board = board, spaces = emptyList(), paths = listOf(path))
		val advancedGame = mockGame(turnOrder = listOf(playerId, otherPlayerId), turnNumber = 1, maxTurns = 10)
		given(gameDao.findById(gameId)).willReturn(game)
		given(playerDao.findByGameId(gameId)).willReturn(listOf(player, otherPlayer))
		given(playerDao.findState(playerId)).willReturn(playerState)
		given(boardDao.findById(boardId)).willReturn(boardGraph)
		given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

		val result = service.takeTurn(gameId, playerId)

		assertThat(result.getOrNull()).hasSize(1)
	}

	@Test
	fun `takeTurn automatically plays the following computer player's turn`() {
		val humanId = Uuid.random()
		val computerId = Uuid.random()
		val startSpaceId = Uuid.random()
		val humanNextSpaceId = Uuid.random()
		val computerSpaceId = Uuid.random()
		val computerNextSpaceId = Uuid.random()
		val turnOrder = listOf(humanId, computerId)
		val game = mockGame(turnOrder = turnOrder, turnNumber = 0, maxTurns = 10)
		val human = mockPlayer(humanId)
		val computer = mockPlayer(computerId, userId = null)
		val humanState = mockPlayerState(PlayerStatus.READY, currentSpaceId = null)
		val computerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = computerSpaceId)
		val board = mockBoard(startSpaceId = startSpaceId)
		val humanPath = mockPath(from = startSpaceId, to = humanNextSpaceId, branchOrder = 0)
		val computerPath = mockPath(from = computerSpaceId, to = computerNextSpaceId, branchOrder = 0)
		val boardGraph = BoardGraph(board = board, spaces = emptyList(), paths = listOf(humanPath, computerPath))
		val afterHumanTurn = mockGame(turnOrder = turnOrder, turnNumber = 1, maxTurns = 10)
		val afterComputerTurn = mockGame(turnOrder = turnOrder, turnNumber = 2, maxTurns = 10)
		given(gameDao.findById(gameId)).willReturn(game)
		given(playerDao.findByGameId(gameId)).willReturn(listOf(human, computer))
		given(playerDao.findState(humanId)).willReturn(humanState)
		given(playerDao.findState(computerId)).willReturn(computerState)
		given(boardDao.findById(boardId)).willReturn(boardGraph)
		given(gameDao.advanceTurn(gameId)).willReturn(afterHumanTurn, afterComputerTurn)

		val result = service.takeTurn(gameId, humanId)

		val turns = result.getOrNull()
		assertThat(turns).hasSize(2)
		assertThat(turns!![0].playerId).isEqualTo(humanId)
		assertThat(turns[0].toSpaceId).isEqualTo(humanNextSpaceId)
		assertThat(turns[1].playerId).isEqualTo(computerId)
		assertThat(turns[1].toSpaceId).isEqualTo(computerNextSpaceId)
		verify(playerDao).updatePosition(humanId, humanNextSpaceId)
		verify(playerDao).updatePosition(computerId, computerNextSpaceId)
	}

	@Test
	fun `takeTurn plays consecutive computer players' turns until the next human's turn`() {
		val humanId = Uuid.random()
		val computerAId = Uuid.random()
		val computerBId = Uuid.random()
		val startSpaceId = Uuid.random()
		val humanNextSpaceId = Uuid.random()
		val computerASpaceId = Uuid.random()
		val computerANextSpaceId = Uuid.random()
		val computerBSpaceId = Uuid.random()
		val computerBNextSpaceId = Uuid.random()
		val turnOrder = listOf(humanId, computerAId, computerBId)
		val game = mockGame(turnOrder = turnOrder, turnNumber = 0, maxTurns = 10)
		val human = mockPlayer(humanId)
		val computerA = mockPlayer(computerAId, userId = null)
		val computerB = mockPlayer(computerBId, userId = null)
		val humanState = mockPlayerState(PlayerStatus.READY, currentSpaceId = null)
		val computerAState = mockPlayerState(PlayerStatus.READY, currentSpaceId = computerASpaceId)
		val computerBState = mockPlayerState(PlayerStatus.READY, currentSpaceId = computerBSpaceId)
		val board = mockBoard(startSpaceId = startSpaceId)
		val humanPath = mockPath(from = startSpaceId, to = humanNextSpaceId, branchOrder = 0)
		val computerAPath = mockPath(from = computerASpaceId, to = computerANextSpaceId, branchOrder = 0)
		val computerBPath = mockPath(from = computerBSpaceId, to = computerBNextSpaceId, branchOrder = 0)
		val boardGraph = BoardGraph(
			board = board,
			spaces = emptyList(),
			paths = listOf(humanPath, computerAPath, computerBPath),
		)
		val afterHuman = mockGame(turnOrder = turnOrder, turnNumber = 1, maxTurns = 10)
		val afterComputerA = mockGame(turnOrder = turnOrder, turnNumber = 2, maxTurns = 10)
		val afterComputerB = mockGame(turnOrder = turnOrder, turnNumber = 3, maxTurns = 10)
		given(gameDao.findById(gameId)).willReturn(game)
		given(playerDao.findByGameId(gameId)).willReturn(listOf(human, computerA, computerB))
		given(playerDao.findState(humanId)).willReturn(humanState)
		given(playerDao.findState(computerAId)).willReturn(computerAState)
		given(playerDao.findState(computerBId)).willReturn(computerBState)
		given(boardDao.findById(boardId)).willReturn(boardGraph)
		given(gameDao.advanceTurn(gameId)).willReturn(afterHuman, afterComputerA, afterComputerB)

		val result = service.takeTurn(gameId, humanId)

		val turns = result.getOrNull()
		assertThat(turns).hasSize(3)
		assertThat(turns!!.map { it.playerId }).containsExactly(humanId, computerAId, computerBId)
	}
}
