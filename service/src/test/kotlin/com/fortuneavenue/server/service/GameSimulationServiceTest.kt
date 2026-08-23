package com.fortuneavenue.server.service

import com.fortuneavenue.server.dao.BoardDao
import com.fortuneavenue.server.dao.GameDao
import com.fortuneavenue.server.dao.GameDistrictInformationDao
import com.fortuneavenue.server.dao.GameShopInformationDao
import com.fortuneavenue.server.dao.PlayerDao
import com.fortuneavenue.server.dao.PlayerStockDao
import com.fortuneavenue.server.models.board.db.Board
import com.fortuneavenue.server.models.board.db.BoardGraph
import com.fortuneavenue.server.models.board.db.BoardPath
import com.fortuneavenue.server.models.board.db.BoardSpace
import com.fortuneavenue.server.models.board.db.BoardSpacesTable
import com.fortuneavenue.server.models.board.db.BoardsTable
import com.fortuneavenue.server.models.board.db.DistrictValueProgression
import com.fortuneavenue.server.models.board.db.DistrictsTable
import com.fortuneavenue.server.models.board.db.GameDistrictInformation
import com.fortuneavenue.server.models.board.db.GameDistrictInformationTable
import com.fortuneavenue.server.models.board.db.GameShopInformation
import com.fortuneavenue.server.models.board.db.GameShopInformationTable
import com.fortuneavenue.server.models.board.db.SpaceType
import com.fortuneavenue.server.models.game.db.Game
import com.fortuneavenue.server.models.player.db.Player
import com.fortuneavenue.server.models.player.db.PlayerState
import com.fortuneavenue.server.models.player.db.PlayerStatus
import com.fortuneavenue.server.models.player.db.PlayerStock
import com.fortuneavenue.server.models.player.db.PlayersTable
import com.fortuneavenue.server.models.user.db.UsersTable
import java.math.BigDecimal
import kotlin.uuid.Uuid
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class GameSimulationServiceTest {

    @Mock lateinit var gameDao: GameDao

    @Mock lateinit var playerDao: PlayerDao

    @Mock lateinit var boardDao: BoardDao

    @Mock lateinit var gameShopInformationDao: GameShopInformationDao

    @Mock lateinit var gameDistrictInformationDao: GameDistrictInformationDao

    @Mock lateinit var playerStockDao: PlayerStockDao

    @Mock lateinit var dice: Dice

    @Mock lateinit var computerPlayer: ComputerPlayer

    private lateinit var service: GameSimulationService

    private val gameId = Uuid.random()
    private val boardId = Uuid.random()

    @BeforeEach
    fun setUp() {
        service =
            GameSimulationService(
                gameDao,
                playerDao,
                boardDao,
                gameShopInformationDao,
                gameDistrictInformationDao,
                playerStockDao,
                dice,
                computerPlayer,
            )
    }

    /** [userId] defaults to a human player -- pass null to mock a computer player instead. */
    private fun mockPlayer(id: Uuid, userId: Uuid? = Uuid.random()): Player {
        val player = mock(Player::class.java)
        lenient().`when`(player.id).thenReturn(EntityID(id, PlayersTable))
        lenient().`when`(player.userId).thenReturn(userId?.let { EntityID(it, UsersTable) })
        return player
    }

    /**
     * [currentGold] defaults generously high so existing tests don't have to think about
     * affordability unless they're actually testing it. [heldSuits] and [promotionCount] default to
     * holding nothing and never having been promoted, so existing tests don't have to think about
     * BANK promotions unless they're actually testing those.
     */
    private fun mockPlayerState(
        status: PlayerStatus,
        currentSpaceId: Uuid? = null,
        currentGold: Int = 1000,
        heldSuits: List<String> = emptyList(),
        promotionCount: Int = 0,
    ): PlayerState {
        val state = mock(PlayerState::class.java)
        lenient().`when`(state.status).thenReturn(status)
        lenient()
            .`when`(state.currentSpaceId)
            .thenReturn(currentSpaceId?.let { EntityID(it, BoardSpacesTable) })
        lenient().`when`(state.currentGold).thenReturn(currentGold)
        lenient().`when`(state.heldSuits).thenReturn(heldSuits)
        lenient().`when`(state.promotionCount).thenReturn(promotionCount)
        return state
    }

    private fun mockPath(from: Uuid, to: Uuid, branchOrder: Int): BoardPath {
        val path = mock(BoardPath::class.java)
        lenient().`when`(path.fromSpaceId).thenReturn(EntityID(from, BoardSpacesTable))
        lenient().`when`(path.toSpaceId).thenReturn(EntityID(to, BoardSpacesTable))
        lenient().`when`(path.branchOrder).thenReturn(branchOrder)
        return path
    }

    private fun mockSpace(id: Uuid, spaceType: SpaceType): BoardSpace {
        val space = mock(BoardSpace::class.java)
        lenient().`when`(space.id).thenReturn(EntityID(id, BoardSpacesTable))
        lenient().`when`(space.spaceType).thenReturn(spaceType)
        return space
    }

    /**
     * [baseSalary] and [promotionBonus] default to arbitrary positive values -- only the promotion
     * tests below care what they actually are.
     */
    private fun mockBoard(
        startSpaceId: Uuid? = null,
        baseSalary: Int = 200,
        promotionBonus: Int = 50,
    ): Board {
        val board = mock(Board::class.java)
        lenient().`when`(board.startSpaceId).thenReturn(startSpaceId)
        lenient().`when`(board.baseSalary).thenReturn(baseSalary)
        lenient().`when`(board.promotionBonus).thenReturn(promotionBonus)
        return board
    }

    private fun mockShop(
        spaceId: Uuid,
        currentValue: Int,
        ownerId: Uuid? = null,
        districtId: Uuid? = null,
    ): GameShopInformation {
        val shop = mock(GameShopInformation::class.java)
        lenient().`when`(shop.id).thenReturn(EntityID(Uuid.random(), GameShopInformationTable))
        lenient().`when`(shop.spaceId).thenReturn(EntityID(spaceId, BoardSpacesTable))
        lenient().`when`(shop.currentValue).thenReturn(currentValue)
        lenient().`when`(shop.ownerId).thenReturn(ownerId?.let { EntityID(it, PlayersTable) })
        lenient()
            .`when`(shop.districtId)
            .thenReturn(districtId?.let { EntityID(it, DistrictsTable) })
        return shop
    }

    /**
     * [minimumStockPercentage] defaults to an arbitrary mid-range value so existing tests don't
     * have to think about it unless they're actually testing [GameSimulationService]'s stock price
     * fluctuation, which reads it to work out a district's price floor.
     */
    private fun mockDistrictInfo(
        districtId: Uuid,
        currentStockValue: Int,
        minimumStockPercentage: BigDecimal = BigDecimal("0.5000"),
    ): GameDistrictInformation {
        val info = mock(GameDistrictInformation::class.java)
        lenient().`when`(info.id).thenReturn(EntityID(Uuid.random(), GameDistrictInformationTable))
        lenient().`when`(info.districtId).thenReturn(EntityID(districtId, DistrictsTable))
        lenient().`when`(info.currentStockValue).thenReturn(currentStockValue)
        lenient().`when`(info.minimumStockPercentage).thenReturn(minimumStockPercentage)
        return info
    }

    private fun mockPlayerStock(
        quantity: Int,
        gameDistrictInformationId: Uuid = Uuid.random(),
    ): PlayerStock {
        val stock = mock(PlayerStock::class.java)
        lenient().`when`(stock.quantity).thenReturn(quantity)
        lenient()
            .`when`(stock.gameDistrictInformationId)
            .thenReturn(EntityID(gameDistrictInformationId, GameDistrictInformationTable))
        return stock
    }

    private fun mockGame(
        turnOrder: List<Uuid>? = null,
        turnNumber: Int = 0,
        maxTurns: Int = 10,
        currentMovementPoints: Int? = null,
        pendingStockTradeSpaceId: Uuid? = null,
        targetNetWorth: Int = 6000,
    ): Game {
        val game = mock(Game::class.java)
        lenient().`when`(game.boardId).thenReturn(EntityID(boardId, BoardsTable))
        lenient().`when`(game.turnOrder).thenReturn(turnOrder)
        lenient().`when`(game.turnNumber).thenReturn(turnNumber)
        lenient().`when`(game.maxTurns).thenReturn(maxTurns)
        lenient().`when`(game.currentMovementPoints).thenReturn(currentMovementPoints)
        lenient()
            .`when`(game.pendingStockTradeSpaceId)
            .thenReturn(pendingStockTradeSpaceId?.let { EntityID(it, BoardSpacesTable) })
        lenient().`when`(game.targetNetWorth).thenReturn(targetNetWorth)
        return game
    }

    /**
     * All orderings of a small, fixed list -- used to stub against every value a `shuffled()` call
     * in production code could produce.
     */
    private fun <T> permutationsOf(items: List<T>): List<List<T>> =
        if (items.size <= 1) {
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
    fun `markReady plays every leading computer player's full turn immediately once the game starts`() {
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
        lenient().`when`(dice.roll()).thenReturn(1)

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
        val movedEvents =
            outcome.openingTurnEvents.filterIsInstance<GameSimulationService.TurnEvent.Moved>()
        assertThat(movedEvents.map { it.playerId }).isEqualTo(leadingComputerIds)
        movedEvents.forEach { moved -> assertThat(moved.toSpaceId).isEqualTo(nextSpaceId) }
    }

    // --- rollDice ---

    @Test
    fun `rollDice fails when the game doesn't exist`() {
        given(gameDao.findById(gameId)).willReturn(null)

        val result = service.rollDice(gameId, Uuid.random())

        assertThat(result.exceptionOrNull()).isInstanceOf(GameNotFoundException::class.java)
    }

    @Test
    fun `rollDice fails when the game hasn't started`() {
        val game = mockGame(turnOrder = null)
        given(gameDao.findById(gameId)).willReturn(game)

        val result = service.rollDice(gameId, Uuid.random())

        assertThat(result.exceptionOrNull()).isInstanceOf(InvalidTurnException::class.java)
    }

    @Test
    fun `rollDice fails when the game is already over`() {
        val playerId = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId), turnNumber = 10, maxTurns = 10)
        given(gameDao.findById(gameId)).willReturn(game)

        val result = service.rollDice(gameId, playerId)

        assertThat(result.exceptionOrNull()).isInstanceOf(InvalidTurnException::class.java)
    }

    @Test
    fun `rollDice fails when it isn't the given player's turn`() {
        val currentPlayerId = Uuid.random()
        val otherPlayerId = Uuid.random()
        val game = mockGame(turnOrder = listOf(currentPlayerId, otherPlayerId))
        given(gameDao.findById(gameId)).willReturn(game)

        val result = service.rollDice(gameId, otherPlayerId)

        assertThat(result.exceptionOrNull()).isInstanceOf(InvalidTurnException::class.java)
    }

    @Test
    fun `rollDice fails when the player already rolled and has a pending path choice`() {
        val playerId = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId), currentMovementPoints = 2)
        given(gameDao.findById(gameId)).willReturn(game)

        val result = service.rollDice(gameId, playerId)

        assertThat(result.exceptionOrNull()).isInstanceOf(InvalidTurnException::class.java)
    }

    @Test
    fun `rollDice moves a player from the board's start space on their first move`() {
        val playerId = Uuid.random()
        val startSpaceId = Uuid.random()
        val nextSpaceId = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId), turnNumber = 0, maxTurns = 10)
        val player = mockPlayer(playerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = null)
        val board = mockBoard(startSpaceId = startSpaceId)
        val path = mockPath(from = startSpaceId, to = nextSpaceId, branchOrder = 0)
        val boardGraph = BoardGraph(board = board, spaces = emptyList(), paths = listOf(path))
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1, maxTurns = 10)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(1)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        val result = service.rollDice(gameId, playerId)

        val events = result.getOrNull()
        assertThat(events)
            .containsExactly(
                GameSimulationService.TurnEvent.DiceRolled(playerId, 1),
                GameSimulationService.TurnEvent.Moved(playerId, 0, startSpaceId, nextSpaceId, 0),
                GameSimulationService.TurnEvent.TurnEnded(playerId, 0, gameOver = false),
                GameSimulationService.TurnEvent.TurnStarted(playerId, 1),
            )
        verify(playerDao).updatePosition(playerId, nextSpaceId)
    }

    @Test
    fun `rollDice moves the player once per remaining point along a chain of single paths`() {
        val playerId = Uuid.random()
        val spaceA = Uuid.random()
        val spaceB = Uuid.random()
        val spaceC = Uuid.random()
        val spaceD = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId))
        val player = mockPlayer(playerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceA)
        val board = mockBoard()
        val boardGraph =
            BoardGraph(
                board = board,
                spaces = emptyList(),
                paths =
                    listOf(
                        mockPath(spaceA, spaceB, 0),
                        mockPath(spaceB, spaceC, 0),
                        mockPath(spaceC, spaceD, 0),
                    ),
            )
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(3)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        val result = service.rollDice(gameId, playerId)

        val events = result.getOrNull()
        assertThat(events)
            .containsExactly(
                GameSimulationService.TurnEvent.DiceRolled(playerId, 3),
                GameSimulationService.TurnEvent.Moved(playerId, 0, spaceA, spaceB, 2),
                GameSimulationService.TurnEvent.Moved(playerId, 0, spaceB, spaceC, 1),
                GameSimulationService.TurnEvent.Moved(playerId, 0, spaceC, spaceD, 0),
                GameSimulationService.TurnEvent.TurnEnded(playerId, 0, gameOver = false),
                GameSimulationService.TurnEvent.TurnStarted(playerId, 1),
            )
        verify(playerDao).updatePosition(playerId, spaceD)
    }

    @Test
    fun `rollDice pauses and lists options when movement reaches a branch for a human player`() {
        val playerId = Uuid.random()
        val spaceA = Uuid.random()
        val spaceB = Uuid.random()
        val branchC = Uuid.random()
        val branchD = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId))
        val player = mockPlayer(playerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceA)
        val board = mockBoard()
        val boardGraph =
            BoardGraph(
                board = board,
                spaces = emptyList(),
                paths =
                    listOf(
                        mockPath(spaceA, spaceB, 0),
                        mockPath(spaceB, branchC, 0),
                        mockPath(spaceB, branchD, 1),
                    ),
            )
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(2)

        val result = service.rollDice(gameId, playerId)

        val events = result.getOrNull()
        assertThat(events)
            .containsExactly(
                GameSimulationService.TurnEvent.DiceRolled(playerId, 2),
                GameSimulationService.TurnEvent.Moved(playerId, 0, spaceA, spaceB, 1),
                GameSimulationService.TurnEvent.ChoiceRequired(
                    playerId,
                    spaceB,
                    listOf(
                        GameSimulationService.PathOption(branchC, 0),
                        GameSimulationService.PathOption(branchD, 1),
                    ),
                ),
            )
        verify(gameDao).setMovementPoints(gameId, 1)
        verify(gameDao, never()).advanceTurn(gameId)
    }

    @Test
    fun `rollDice lets a computer player pick a branch randomly instead of pausing`() {
        val playerId = Uuid.random()
        // A second, human player after this one in turn order -- otherwise,
        // with a turn order of just the one computer player, ending its turn
        // would hand play right back to that same computer again (and
        // again): fine in production, where advanceTurn genuinely moves
        // turnNumber toward maxTurns each time, but this test's fixed,
        // non-incrementing advanceTurn stub would make that loop forever.
        val otherPlayerId = Uuid.random()
        val spaceA = Uuid.random()
        val spaceB = Uuid.random()
        val branchC = Uuid.random()
        val branchD = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId, otherPlayerId))
        val player = mockPlayer(playerId, userId = null)
        val otherPlayer = mockPlayer(otherPlayerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceA)
        val board = mockBoard()
        val pathToC = mockPath(spaceB, branchC, 0)
        val pathToD = mockPath(spaceB, branchD, 1)
        val boardGraph =
            BoardGraph(
                board = board,
                spaces = emptyList(),
                paths = listOf(mockPath(spaceA, spaceB, 0), pathToC, pathToD),
            )
        val advancedGame = mockGame(turnOrder = listOf(playerId, otherPlayerId), turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player, otherPlayer))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(2)
        given(computerPlayer.chooseBranch(listOf(pathToC, pathToD))).willReturn(pathToD)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        val result = service.rollDice(gameId, playerId)

        val events = result.getOrNull()
        assertThat(events)
            .containsExactly(
                GameSimulationService.TurnEvent.DiceRolled(playerId, 2),
                GameSimulationService.TurnEvent.Moved(playerId, 0, spaceA, spaceB, 1),
                GameSimulationService.TurnEvent.Moved(playerId, 0, spaceB, branchD, 0),
                GameSimulationService.TurnEvent.TurnEnded(playerId, 0, gameOver = false),
                GameSimulationService.TurnEvent.TurnStarted(otherPlayerId, 1),
            )
        verify(playerDao).updatePosition(playerId, branchD)
    }

    @Test
    fun `rollDice reports the game as over once it reaches max turns`() {
        val playerId = Uuid.random()
        val spaceId = Uuid.random()
        val nextSpaceId = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId), turnNumber = 9, maxTurns = 10)
        val player = mockPlayer(playerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceId)
        val board = mockBoard()
        val path = mockPath(spaceId, nextSpaceId, 0)
        val boardGraph = BoardGraph(board = board, spaces = emptyList(), paths = listOf(path))
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 10, maxTurns = 10)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(1)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        val result = service.rollDice(gameId, playerId)

        val turnEnded =
            result
                .getOrNull()
                ?.filterIsInstance<GameSimulationService.TurnEvent.TurnEnded>()
                ?.single()
        assertThat(turnEnded?.gameOver).isTrue()
    }

    @Test
    fun `rollDice fails when there's no path forward from the current space`() {
        val playerId = Uuid.random()
        val spaceId = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId))
        val player = mockPlayer(playerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceId)
        val board = mockBoard()
        val boardGraph = BoardGraph(board = board, spaces = emptyList(), paths = emptyList())
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(1)

        val result = service.rollDice(gameId, playerId)

        assertThat(result.exceptionOrNull()).isInstanceOf(InvalidTurnException::class.java)
    }

    @Test
    fun `rollDice does not auto-play the next human player's turn`() {
        val playerId = Uuid.random()
        val otherPlayerId = Uuid.random()
        val startSpaceId = Uuid.random()
        val nextSpaceId = Uuid.random()
        val game =
            mockGame(turnOrder = listOf(playerId, otherPlayerId), turnNumber = 0, maxTurns = 10)
        val player = mockPlayer(playerId)
        val otherPlayer = mockPlayer(otherPlayerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = null)
        val board = mockBoard(startSpaceId = startSpaceId)
        val path = mockPath(from = startSpaceId, to = nextSpaceId, branchOrder = 0)
        val boardGraph = BoardGraph(board = board, spaces = emptyList(), paths = listOf(path))
        val advancedGame =
            mockGame(turnOrder = listOf(playerId, otherPlayerId), turnNumber = 1, maxTurns = 10)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player, otherPlayer))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(1)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        val result = service.rollDice(gameId, playerId)

        val events = result.getOrNull()
        assertThat(events).isNotNull()
        // Nothing about otherPlayerId's turn gets played -- the only event
        // naming them is the trailing announcement that it's their turn now.
        assertThat(events!!.dropLast(1).map { it.playerId }).containsOnly(playerId)
        assertThat(events.last())
            .isEqualTo(GameSimulationService.TurnEvent.TurnStarted(otherPlayerId, 1))
    }

    @Test
    fun `rollDice automatically plays the following computer player's full turn`() {
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
        val computerPath =
            mockPath(from = computerSpaceId, to = computerNextSpaceId, branchOrder = 0)
        val boardGraph =
            BoardGraph(board = board, spaces = emptyList(), paths = listOf(humanPath, computerPath))
        val afterHumanTurn = mockGame(turnOrder = turnOrder, turnNumber = 1, maxTurns = 10)
        val afterComputerTurn = mockGame(turnOrder = turnOrder, turnNumber = 2, maxTurns = 10)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(human, computer))
        given(playerDao.findState(humanId)).willReturn(humanState)
        given(playerDao.findState(computerId)).willReturn(computerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(1)
        given(gameDao.advanceTurn(gameId)).willReturn(afterHumanTurn, afterComputerTurn)

        val result = service.rollDice(gameId, humanId)

        val events = result.getOrNull()
        assertThat(events).isNotNull()
        val movedEvents = events!!.filterIsInstance<GameSimulationService.TurnEvent.Moved>()
        assertThat(movedEvents.map { it.playerId }).containsExactly(humanId, computerId)
        assertThat(movedEvents.map { it.toSpaceId })
            .containsExactly(humanNextSpaceId, computerNextSpaceId)
        verify(playerDao).updatePosition(humanId, humanNextSpaceId)
        verify(playerDao).updatePosition(computerId, computerNextSpaceId)
    }

    @Test
    fun `rollDice plays consecutive computer players' turns until the next human's turn`() {
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
        val computerAPath =
            mockPath(from = computerASpaceId, to = computerANextSpaceId, branchOrder = 0)
        val computerBPath =
            mockPath(from = computerBSpaceId, to = computerBNextSpaceId, branchOrder = 0)
        val boardGraph =
            BoardGraph(
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
        given(dice.roll()).willReturn(1)
        given(gameDao.advanceTurn(gameId)).willReturn(afterHuman, afterComputerA, afterComputerB)

        val result = service.rollDice(gameId, humanId)

        val events = result.getOrNull()
        assertThat(events).isNotNull()
        val movedEvents = events!!.filterIsInstance<GameSimulationService.TurnEvent.Moved>()
        assertThat(movedEvents.map { it.playerId })
            .containsExactly(humanId, computerAId, computerBId)
    }

    // --- suit pickups ---

    @Test
    fun `rollDice picks up a suit for the first time landing on a suit space`() {
        val playerId = Uuid.random()
        val startSpaceId = Uuid.random()
        val nextSpaceId = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId), turnNumber = 0, maxTurns = 10)
        val player = mockPlayer(playerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = null)
        val board = mockBoard(startSpaceId = startSpaceId)
        val path = mockPath(from = startSpaceId, to = nextSpaceId, branchOrder = 0)
        val space = mockSpace(nextSpaceId, SpaceType.HEART)
        val boardGraph = BoardGraph(board = board, spaces = listOf(space), paths = listOf(path))
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1, maxTurns = 10)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(1)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)
        given(playerDao.addHeldSuit(playerId, SpaceType.HEART)).willReturn(true)

        val result = service.rollDice(gameId, playerId)

        val events = result.getOrNull()
        assertThat(events)
            .containsExactly(
                GameSimulationService.TurnEvent.DiceRolled(playerId, 1),
                GameSimulationService.TurnEvent.Moved(playerId, 0, startSpaceId, nextSpaceId, 0),
                GameSimulationService.TurnEvent.SuitPickedUp(
                    playerId,
                    nextSpaceId,
                    SpaceType.HEART,
                ),
                GameSimulationService.TurnEvent.TurnEnded(playerId, 0, gameOver = false),
                GameSimulationService.TurnEvent.TurnStarted(playerId, 1),
            )
        verify(playerDao).addHeldSuit(playerId, SpaceType.HEART)
    }

    @Test
    fun `rollDice picks up a suit for a space merely passed through, not just the one movement ends on`() {
        val playerId = Uuid.random()
        val spaceA = Uuid.random()
        val spaceB = Uuid.random()
        val spaceC = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId))
        val player = mockPlayer(playerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceA)
        val board = mockBoard()
        val passedSpace = mockSpace(spaceB, SpaceType.DIAMOND)
        val boardGraph =
            BoardGraph(
                board = board,
                spaces = listOf(passedSpace),
                paths = listOf(mockPath(spaceA, spaceB, 0), mockPath(spaceB, spaceC, 0)),
            )
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(2)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)
        given(playerDao.addHeldSuit(playerId, SpaceType.DIAMOND)).willReturn(true)

        val result = service.rollDice(gameId, playerId)

        val events = result.getOrNull()
        assertThat(events)
            .contains(
                GameSimulationService.TurnEvent.SuitPickedUp(playerId, spaceB, SpaceType.DIAMOND)
            )
        verify(playerDao).addHeldSuit(playerId, SpaceType.DIAMOND)
    }

    @Test
    fun `rollDice landing on a suit already held emits no SuitPickedUp event`() {
        val playerId = Uuid.random()
        val startSpaceId = Uuid.random()
        val nextSpaceId = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId), turnNumber = 0, maxTurns = 10)
        val player = mockPlayer(playerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = null)
        val board = mockBoard(startSpaceId = startSpaceId)
        val path = mockPath(from = startSpaceId, to = nextSpaceId, branchOrder = 0)
        val space = mockSpace(nextSpaceId, SpaceType.CLUB)
        val boardGraph = BoardGraph(board = board, spaces = listOf(space), paths = listOf(path))
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1, maxTurns = 10)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(1)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)
        given(playerDao.addHeldSuit(playerId, SpaceType.CLUB)).willReturn(false)

        val result = service.rollDice(gameId, playerId)

        val events = result.getOrNull()
        assertThat(events)
            .containsExactly(
                GameSimulationService.TurnEvent.DiceRolled(playerId, 1),
                GameSimulationService.TurnEvent.Moved(playerId, 0, startSpaceId, nextSpaceId, 0),
                GameSimulationService.TurnEvent.TurnEnded(playerId, 0, gameOver = false),
                GameSimulationService.TurnEvent.TurnStarted(playerId, 1),
            )
    }

    @Test
    fun `rollDice landing on a non-suit space never calls addHeldSuit`() {
        val playerId = Uuid.random()
        val startSpaceId = Uuid.random()
        val nextSpaceId = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId), turnNumber = 0, maxTurns = 10)
        val player = mockPlayer(playerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = null)
        val board = mockBoard(startSpaceId = startSpaceId)
        val path = mockPath(from = startSpaceId, to = nextSpaceId, branchOrder = 0)
        val space = mockSpace(nextSpaceId, SpaceType.BASIC)
        val boardGraph = BoardGraph(board = board, spaces = listOf(space), paths = listOf(path))
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1, maxTurns = 10)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(1)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        val result = service.rollDice(gameId, playerId)

        assertThat(
                result.getOrNull()?.filterIsInstance<GameSimulationService.TurnEvent.SuitPickedUp>()
            )
            .isEmpty()
    }

    // --- promotions ---

    private val allSuitNames =
        listOf(SpaceType.HEART, SpaceType.DIAMOND, SpaceType.SPADE, SpaceType.CLUB).map { it.name }

    @Test
    fun `rollDice landing on a BANK space while holding all 4 suits triggers a promotion, paying baseSalary plus owned shop value on the first promotion`() {
        val playerId = Uuid.random()
        val startSpaceId = Uuid.random()
        val nextSpaceId = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId), turnNumber = 0, maxTurns = 10)
        val player = mockPlayer(playerId)
        val playerState =
            mockPlayerState(
                PlayerStatus.READY,
                currentSpaceId = null,
                heldSuits = allSuitNames,
                promotionCount = 0,
            )
        val board = mockBoard(startSpaceId = startSpaceId, baseSalary = 200, promotionBonus = 50)
        val path = mockPath(from = startSpaceId, to = nextSpaceId, branchOrder = 0)
        val space = mockSpace(nextSpaceId, SpaceType.BANK)
        val boardGraph = BoardGraph(board = board, spaces = listOf(space), paths = listOf(path))
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1, maxTurns = 10)
        val ownedShops =
            listOf(
                mockShop(Uuid.random(), currentValue = 150),
                mockShop(Uuid.random(), currentValue = 100),
            )
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(1)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)
        given(gameShopInformationDao.findOwnedByPlayer(gameId, playerId)).willReturn(ownedShops)

        val result = service.rollDice(gameId, playerId)

        // 200 (baseSalary) + 50 * 0 (promotionBonus * promotionCount, still 0 the first time) + 250
        // (owned shops)
        val events = result.getOrNull()
        assertThat(events)
            .containsExactly(
                GameSimulationService.TurnEvent.DiceRolled(playerId, 1),
                GameSimulationService.TurnEvent.Moved(playerId, 0, startSpaceId, nextSpaceId, 0),
                GameSimulationService.TurnEvent.Promoted(playerId, nextSpaceId, 450),
                GameSimulationService.TurnEvent.TurnEnded(playerId, 0, gameOver = false),
                GameSimulationService.TurnEvent.TurnStarted(playerId, 1),
            )
        verify(playerDao).clearHeldSuits(playerId)
        verify(playerDao).incrementPromotionCount(playerId)
        verify(playerDao).adjustGold(playerId, 450)
    }

    @Test
    fun `the promotion payout multiplies promotionBonus by however many times the player's already been promoted`() {
        val playerId = Uuid.random()
        val startSpaceId = Uuid.random()
        val nextSpaceId = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId), turnNumber = 0, maxTurns = 10)
        val player = mockPlayer(playerId)
        val playerState =
            mockPlayerState(
                PlayerStatus.READY,
                currentSpaceId = null,
                heldSuits = allSuitNames,
                promotionCount = 3,
            )
        val board = mockBoard(startSpaceId = startSpaceId, baseSalary = 200, promotionBonus = 50)
        val path = mockPath(from = startSpaceId, to = nextSpaceId, branchOrder = 0)
        val space = mockSpace(nextSpaceId, SpaceType.BANK)
        val boardGraph = BoardGraph(board = board, spaces = listOf(space), paths = listOf(path))
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1, maxTurns = 10)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(1)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)
        given(gameShopInformationDao.findOwnedByPlayer(gameId, playerId)).willReturn(emptyList())

        val result = service.rollDice(gameId, playerId)

        // 200 (baseSalary) + 50 * 3 (promotionBonus * promotionCount) + 0 (no owned shops) = 350
        val promoted =
            result
                .getOrNull()
                ?.filterIsInstance<GameSimulationService.TurnEvent.Promoted>()
                ?.single()
        assertThat(promoted?.goldAwarded).isEqualTo(350)
        verify(playerDao).adjustGold(playerId, 350)
    }

    @Test
    fun `rollDice passing through a BANK space mid-move while holding all 4 suits also triggers a promotion`() {
        val playerId = Uuid.random()
        val spaceA = Uuid.random()
        val spaceB = Uuid.random()
        val spaceC = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId))
        val player = mockPlayer(playerId)
        val playerState =
            mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceA, heldSuits = allSuitNames)
        val board = mockBoard()
        val passedSpace = mockSpace(spaceB, SpaceType.BANK)
        val boardGraph =
            BoardGraph(
                board = board,
                spaces = listOf(passedSpace),
                paths = listOf(mockPath(spaceA, spaceB, 0), mockPath(spaceB, spaceC, 0)),
            )
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(2)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)
        given(gameShopInformationDao.findOwnedByPlayer(gameId, playerId)).willReturn(emptyList())

        val result = service.rollDice(gameId, playerId)

        val events = result.getOrNull()
        assertThat(events).anyMatch {
            it is GameSimulationService.TurnEvent.Promoted && it.spaceId == spaceB
        }
        verify(playerDao).clearHeldSuits(playerId)
        verify(playerDao).incrementPromotionCount(playerId)
    }

    @Test
    fun `rollDice landing on a BANK space without all 4 suits emits no Promoted event, and leaves suits, promotion count, and gold untouched`() {
        val playerId = Uuid.random()
        val startSpaceId = Uuid.random()
        val nextSpaceId = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId), turnNumber = 0, maxTurns = 10)
        val player = mockPlayer(playerId)
        // Missing CLUB -- not a complete set.
        val incompleteSuits =
            listOf(SpaceType.HEART, SpaceType.DIAMOND, SpaceType.SPADE).map { it.name }
        val playerState =
            mockPlayerState(PlayerStatus.READY, currentSpaceId = null, heldSuits = incompleteSuits)
        val board = mockBoard(startSpaceId = startSpaceId)
        val path = mockPath(from = startSpaceId, to = nextSpaceId, branchOrder = 0)
        val space = mockSpace(nextSpaceId, SpaceType.BANK)
        val boardGraph = BoardGraph(board = board, spaces = listOf(space), paths = listOf(path))
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1, maxTurns = 10)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(1)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        val result = service.rollDice(gameId, playerId)

        val events = result.getOrNull()
        assertThat(events)
            .containsExactly(
                GameSimulationService.TurnEvent.DiceRolled(playerId, 1),
                GameSimulationService.TurnEvent.Moved(playerId, 0, startSpaceId, nextSpaceId, 0),
                GameSimulationService.TurnEvent.TurnEnded(playerId, 0, gameOver = false),
                GameSimulationService.TurnEvent.TurnStarted(playerId, 1),
            )
        verify(playerDao, never()).clearHeldSuits(playerId)
        verify(playerDao, never()).incrementPromotionCount(playerId)
        verify(gameShopInformationDao, never()).findOwnedByPlayer(gameId, playerId)
    }

    @Test
    fun `rollDice landing on a non-BANK space never calls clearHeldSuits`() {
        val playerId = Uuid.random()
        val startSpaceId = Uuid.random()
        val nextSpaceId = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId), turnNumber = 0, maxTurns = 10)
        val player = mockPlayer(playerId)
        val playerState =
            mockPlayerState(PlayerStatus.READY, currentSpaceId = null, heldSuits = allSuitNames)
        val board = mockBoard(startSpaceId = startSpaceId)
        val path = mockPath(from = startSpaceId, to = nextSpaceId, branchOrder = 0)
        val space = mockSpace(nextSpaceId, SpaceType.BASIC)
        val boardGraph = BoardGraph(board = board, spaces = listOf(space), paths = listOf(path))
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1, maxTurns = 10)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(1)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        service.rollDice(gameId, playerId)

        verify(playerDao, never()).clearHeldSuits(playerId)
        verify(playerDao, never()).incrementPromotionCount(playerId)
    }

    // --- stock trading ---

    @Test
    fun `rollDice pauses and offers stock trading when movement ends on a BANK space with district stock to trade, for a human player`() {
        val playerId = Uuid.random()
        val spaceId = Uuid.random()
        val bankSpaceId = Uuid.random()
        val districtId = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId))
        val player = mockPlayer(playerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceId)
        val board = mockBoard()
        val bankSpace = mockSpace(bankSpaceId, SpaceType.BANK)
        val boardGraph =
            BoardGraph(
                board = board,
                spaces = listOf(bankSpace),
                paths = listOf(mockPath(spaceId, bankSpaceId, 0)),
            )
        val districtInfo = mockDistrictInfo(districtId, currentStockValue = 300)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(1)
        given(gameDistrictInformationDao.findAllByGame(gameId)).willReturn(listOf(districtInfo))

        val result = service.rollDice(gameId, playerId)

        val events = result.getOrNull()
        assertThat(events)
            .containsExactly(
                GameSimulationService.TurnEvent.DiceRolled(playerId, 1),
                GameSimulationService.TurnEvent.Moved(playerId, 0, spaceId, bankSpaceId, 0),
                GameSimulationService.TurnEvent.StockTradingAvailable(
                    playerId,
                    bankSpaceId,
                    listOf(StockTradeOffer(districtId, districtInfo.id.value, 300, 0)),
                ),
            )
        verify(gameDao).setMovementPoints(gameId, 0)
        verify(gameDao).setPendingStockTradeSpace(gameId, bankSpaceId)
        verify(gameDao, never()).advanceTurn(gameId)
    }

    @Test
    fun `rollDice pauses for stock trading even with movement left, when a BANK space is merely passed through`() {
        val playerId = Uuid.random()
        val spaceA = Uuid.random()
        val spaceB = Uuid.random()
        val spaceC = Uuid.random()
        val districtId = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId))
        val player = mockPlayer(playerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceA)
        val board = mockBoard()
        val passedSpace = mockSpace(spaceB, SpaceType.BANK)
        val boardGraph =
            BoardGraph(
                board = board,
                spaces = listOf(passedSpace),
                paths = listOf(mockPath(spaceA, spaceB, 0), mockPath(spaceB, spaceC, 0)),
            )
        val districtInfo = mockDistrictInfo(districtId, currentStockValue = 150)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(2)
        given(gameDistrictInformationDao.findAllByGame(gameId)).willReturn(listOf(districtInfo))

        val result = service.rollDice(gameId, playerId)

        val events = result.getOrNull()
        assertThat(events)
            .containsExactly(
                GameSimulationService.TurnEvent.DiceRolled(playerId, 2),
                GameSimulationService.TurnEvent.Moved(playerId, 0, spaceA, spaceB, 1),
                GameSimulationService.TurnEvent.StockTradingAvailable(
                    playerId,
                    spaceB,
                    listOf(StockTradeOffer(districtId, districtInfo.id.value, 150, 0)),
                ),
            )
        // One movement point was still left when the pause happened -- not exhausted like a shop
        // purchase.
        verify(gameDao).setMovementPoints(gameId, 1)
    }

    @Test
    fun `rollDice landing on a BANK space with no district stock to trade emits no stock trading event`() {
        val playerId = Uuid.random()
        val startSpaceId = Uuid.random()
        val bankSpaceId = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId), turnNumber = 0, maxTurns = 10)
        val player = mockPlayer(playerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = null)
        val board = mockBoard(startSpaceId = startSpaceId)
        val path = mockPath(from = startSpaceId, to = bankSpaceId, branchOrder = 0)
        val bankSpace = mockSpace(bankSpaceId, SpaceType.BANK)
        val boardGraph = BoardGraph(board = board, spaces = listOf(bankSpace), paths = listOf(path))
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1, maxTurns = 10)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(1)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        val result = service.rollDice(gameId, playerId)

        assertThat(
                result
                    .getOrNull()
                    ?.filterIsInstance<GameSimulationService.TurnEvent.StockTradingAvailable>()
            )
            .isEmpty()
        verify(gameDao, never()).setPendingStockTradeSpace(gameId, bankSpaceId)
    }

    @Test
    fun `buyStock fails when there's no stock trade decision pending`() {
        val playerId = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId), pendingStockTradeSpaceId = null)
        given(gameDao.findById(gameId)).willReturn(game)

        val result = service.buyStock(gameId, playerId, Uuid.random(), 10)

        assertThat(result.exceptionOrNull()).isInstanceOf(InvalidTurnException::class.java)
    }

    @Test
    fun `buyStock fails when quantity is outside the 1-99 range`() {
        val playerId = Uuid.random()
        val bankSpaceId = Uuid.random()
        val game =
            mockGame(
                turnOrder = listOf(playerId),
                currentMovementPoints = 0,
                pendingStockTradeSpaceId = bankSpaceId,
            )
        given(gameDao.findById(gameId)).willReturn(game)

        val tooFew = service.buyStock(gameId, playerId, Uuid.random(), 0)
        val tooMany = service.buyStock(gameId, playerId, Uuid.random(), 100)

        assertThat(tooFew.exceptionOrNull()).isInstanceOf(InvalidTurnException::class.java)
        assertThat(tooMany.exceptionOrNull()).isInstanceOf(InvalidTurnException::class.java)
    }

    @Test
    fun `buyStock deducts total cost, credits stock quantity, and ends the turn when no movement is left`() {
        val playerId = Uuid.random()
        val districtId = Uuid.random()
        val bankSpaceId = Uuid.random()
        val game =
            mockGame(
                turnOrder = listOf(playerId),
                turnNumber = 0,
                currentMovementPoints = 0,
                pendingStockTradeSpaceId = bankSpaceId,
            )
        val player = mockPlayer(playerId)
        val districtInfo = mockDistrictInfo(districtId, currentStockValue = 50)
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(gameDistrictInformationDao.findByGameAndDistrict(gameId, districtId))
            .willReturn(districtInfo)
        val playerState = mockPlayerState(PlayerStatus.READY)
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        val result = service.buyStock(gameId, playerId, districtId, 10)

        assertThat(result.getOrNull())
            .containsExactly(
                GameSimulationService.TurnEvent.StockPurchased(playerId, districtId, 10, 50, 500),
                GameSimulationService.TurnEvent.TurnEnded(playerId, 0, gameOver = false),
                GameSimulationService.TurnEvent.TurnStarted(playerId, 1),
            )
        verify(playerDao).adjustGold(playerId, -500)
        verify(playerStockDao).adjustQuantity(playerId, districtInfo.id.value, 10)
        verify(gameDao).setPendingStockTradeSpace(gameId, null)
    }

    @Test
    fun `buyStock ends the game early once the buyer's stock holdings alone reach the target net worth`() {
        val playerId = Uuid.random()
        val districtId = Uuid.random()
        val bankSpaceId = Uuid.random()
        val game =
            mockGame(
                turnOrder = listOf(playerId),
                turnNumber = 0,
                currentMovementPoints = 0,
                pendingStockTradeSpaceId = bankSpaceId,
                targetNetWorth = 6000,
            )
        val player = mockPlayer(playerId)
        val districtInfo = mockDistrictInfo(districtId, currentStockValue = 50)
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(gameDistrictInformationDao.findByGameAndDistrict(gameId, districtId))
            .willReturn(districtInfo)
        val playerState = mockPlayerState(PlayerStatus.READY)
        given(playerDao.findState(playerId)).willReturn(playerState)
        // 120 shares at 50 gold each is 6000 -- findByPlayer/findById are separate mocks from
        // adjustQuantity, so this models what PlayerStockDao and GameDistrictInformationDao would
        // report afterward, exactly as findOwnedByPlayer is stubbed directly above for shops.
        val ownedStock =
            mockPlayerStock(quantity = 120, gameDistrictInformationId = districtInfo.id.value)
        given(playerStockDao.findByPlayer(playerId)).willReturn(listOf(ownedStock))
        given(gameDistrictInformationDao.findById(districtInfo.id.value)).willReturn(districtInfo)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        service.buyStock(gameId, playerId, districtId, 10)

        verify(gameDao).endGameEarly(gameId)
    }

    @Test
    fun `buyStock fails when the player can't afford the total cost, leaving gold and quantity untouched`() {
        val playerId = Uuid.random()
        val districtId = Uuid.random()
        val bankSpaceId = Uuid.random()
        val game =
            mockGame(
                turnOrder = listOf(playerId),
                currentMovementPoints = 0,
                pendingStockTradeSpaceId = bankSpaceId,
            )
        val districtInfo = mockDistrictInfo(districtId, currentStockValue = 50)
        given(gameDao.findById(gameId)).willReturn(game)
        given(gameDistrictInformationDao.findByGameAndDistrict(gameId, districtId))
            .willReturn(districtInfo)
        val playerState = mockPlayerState(PlayerStatus.READY, currentGold = 100)
        given(playerDao.findState(playerId)).willReturn(playerState)

        val result = service.buyStock(gameId, playerId, districtId, 10)

        assertThat(result.exceptionOrNull()).isInstanceOf(InvalidTurnException::class.java)
        verify(playerDao, never()).adjustGold(playerId, -500)
        verify(playerStockDao, never()).adjustQuantity(playerId, districtInfo.id.value, 10)
        verify(gameDao, never()).advanceTurn(gameId)
    }

    @Test
    fun `sellStock fails when quantity exceeds what the player owns`() {
        val playerId = Uuid.random()
        val districtId = Uuid.random()
        val bankSpaceId = Uuid.random()
        val game =
            mockGame(
                turnOrder = listOf(playerId),
                currentMovementPoints = 0,
                pendingStockTradeSpaceId = bankSpaceId,
            )
        val districtInfo = mockDistrictInfo(districtId, currentStockValue = 50)
        val ownedStock = mockPlayerStock(quantity = 5)
        given(gameDao.findById(gameId)).willReturn(game)
        given(gameDistrictInformationDao.findByGameAndDistrict(gameId, districtId))
            .willReturn(districtInfo)
        given(playerStockDao.find(playerId, districtInfo.id.value)).willReturn(ownedStock)

        val result = service.sellStock(gameId, playerId, districtId, 10)

        assertThat(result.exceptionOrNull()).isInstanceOf(InvalidTurnException::class.java)
        verify(playerDao, never()).adjustGold(playerId, 500)
    }

    @Test
    fun `sellStock credits proceeds to the player's gold and reduces their stock quantity, then ends the turn`() {
        val playerId = Uuid.random()
        val districtId = Uuid.random()
        val bankSpaceId = Uuid.random()
        val game =
            mockGame(
                turnOrder = listOf(playerId),
                turnNumber = 0,
                currentMovementPoints = 0,
                pendingStockTradeSpaceId = bankSpaceId,
            )
        val player = mockPlayer(playerId)
        val districtInfo = mockDistrictInfo(districtId, currentStockValue = 40)
        val ownedStock = mockPlayerStock(quantity = 20)
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(gameDistrictInformationDao.findByGameAndDistrict(gameId, districtId))
            .willReturn(districtInfo)
        given(playerStockDao.find(playerId, districtInfo.id.value)).willReturn(ownedStock)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        val result = service.sellStock(gameId, playerId, districtId, 15)

        assertThat(result.getOrNull())
            .containsExactly(
                GameSimulationService.TurnEvent.StockSold(playerId, districtId, 15, 40, 600),
                GameSimulationService.TurnEvent.TurnEnded(playerId, 0, gameOver = false),
                GameSimulationService.TurnEvent.TurnStarted(playerId, 1),
            )
        verify(playerDao).adjustGold(playerId, 600)
        verify(playerStockDao).adjustQuantity(playerId, districtInfo.id.value, -15)
    }

    @Test
    fun `buyStock fluctuates the district's stock price upward when quantity exceeds the threshold`() {
        val playerId = Uuid.random()
        val districtId = Uuid.random()
        val bankSpaceId = Uuid.random()
        val game =
            mockGame(
                turnOrder = listOf(playerId),
                turnNumber = 0,
                currentMovementPoints = 0,
                pendingStockTradeSpaceId = bankSpaceId,
            )
        val player = mockPlayer(playerId)
        val districtInfo = mockDistrictInfo(districtId, currentStockValue = 50)
        val shops = listOf(mockShop(Uuid.random(), currentValue = 100, districtId = districtId))
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(gameDistrictInformationDao.findByGameAndDistrict(gameId, districtId))
            .willReturn(districtInfo)
        given(gameShopInformationDao.findByGameAndDistrict(gameId, districtInfo.districtId))
            .willReturn(shops)
        val playerState = mockPlayerState(PlayerStatus.READY)
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        // 11 shares is more than the 10-share threshold -- unlike the 10-share purchase above,
        // this one should also nudge the district's price up: 50 / 16 = 3 (rounded down), + 1 = 4,
        // so 50 -> 54 (well above the floor of average(100) * 0.5 = 50).
        service.buyStock(gameId, playerId, districtId, 11)

        verify(gameDistrictInformationDao).setCurrentStockValue(districtInfo.id.value, 54)
    }

    @Test
    fun `buyStock leaves the district's stock price alone when quantity is at or below the threshold`() {
        val playerId = Uuid.random()
        val districtId = Uuid.random()
        val bankSpaceId = Uuid.random()
        val game =
            mockGame(
                turnOrder = listOf(playerId),
                turnNumber = 0,
                currentMovementPoints = 0,
                pendingStockTradeSpaceId = bankSpaceId,
            )
        val player = mockPlayer(playerId)
        val districtInfo = mockDistrictInfo(districtId, currentStockValue = 50)
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(gameDistrictInformationDao.findByGameAndDistrict(gameId, districtId))
            .willReturn(districtInfo)
        val playerState = mockPlayerState(PlayerStatus.READY)
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        service.buyStock(gameId, playerId, districtId, 10)

        // The net worth check (see GameSimulationService.endGameIfNetWorthReached) now
        // legitimately reads owned shops on every stock trade -- the meaningful assertion here is
        // that the district's own stock price was never touched. setCurrentStockValue takes a
        // non-null Uuid, so an eq()/anyInt() matcher pair returns null for it and NPEs against
        // Kotlin's null-check on that parameter -- checking recorded invocations directly
        // sidesteps invoking the real method at all.
        val stockPriceCalls =
            mockingDetails(gameDistrictInformationDao).invocations.filter {
                it.method.name == "setCurrentStockValue"
            }
        assertThat(stockPriceCalls).isEmpty()
    }

    @Test
    fun `sellStock fluctuates the district's stock price downward when there's room above the minimum`() {
        val playerId = Uuid.random()
        val districtId = Uuid.random()
        val bankSpaceId = Uuid.random()
        val game =
            mockGame(
                turnOrder = listOf(playerId),
                turnNumber = 0,
                currentMovementPoints = 0,
                pendingStockTradeSpaceId = bankSpaceId,
            )
        val player = mockPlayer(playerId)
        val districtInfo = mockDistrictInfo(districtId, currentStockValue = 40)
        val ownedStock = mockPlayerStock(quantity = 20)
        // A low shop value keeps the floor (average(10) * 0.5 = 5) well out of the way, so the
        // fluctuation isn't clamped -- see the dedicated floor test below for that case.
        val shops = listOf(mockShop(Uuid.random(), currentValue = 10, districtId = districtId))
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(gameDistrictInformationDao.findByGameAndDistrict(gameId, districtId))
            .willReturn(districtInfo)
        given(playerStockDao.find(playerId, districtInfo.id.value)).willReturn(ownedStock)
        given(gameShopInformationDao.findByGameAndDistrict(gameId, districtInfo.districtId))
            .willReturn(shops)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        // 15 shares is more than the 10-share threshold -- this one should nudge the district's
        // price down: 40 / 16 = 2 (rounded down), + 1 = 3, so 40 -> 37.
        service.sellStock(gameId, playerId, districtId, 15)

        verify(gameDistrictInformationDao).setCurrentStockValue(districtInfo.id.value, 37)
    }

    @Test
    fun `sellStock never drops the district's stock price below its current minimum`() {
        val playerId = Uuid.random()
        val districtId = Uuid.random()
        val bankSpaceId = Uuid.random()
        val game =
            mockGame(
                turnOrder = listOf(playerId),
                turnNumber = 0,
                currentMovementPoints = 0,
                pendingStockTradeSpaceId = bankSpaceId,
            )
        val player = mockPlayer(playerId)
        val districtInfo = mockDistrictInfo(districtId, currentStockValue = 40)
        val ownedStock = mockPlayerStock(quantity = 20)
        // average(80) * 0.5 = 40 -- exactly what an unclamped fluctuation (40 - (40 / 16 + 1) =
        // 37) would otherwise fall below.
        val shops = listOf(mockShop(Uuid.random(), currentValue = 80, districtId = districtId))
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(gameDistrictInformationDao.findByGameAndDistrict(gameId, districtId))
            .willReturn(districtInfo)
        given(playerStockDao.find(playerId, districtInfo.id.value)).willReturn(ownedStock)
        given(gameShopInformationDao.findByGameAndDistrict(gameId, districtInfo.districtId))
            .willReturn(shops)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        service.sellStock(gameId, playerId, districtId, 15)

        verify(gameDistrictInformationDao).setCurrentStockValue(districtInfo.id.value, 40)
    }

    @Test
    fun `sellStock leaves the district's stock price alone when quantity is at or below the threshold`() {
        val playerId = Uuid.random()
        val districtId = Uuid.random()
        val bankSpaceId = Uuid.random()
        val game =
            mockGame(
                turnOrder = listOf(playerId),
                turnNumber = 0,
                currentMovementPoints = 0,
                pendingStockTradeSpaceId = bankSpaceId,
            )
        val player = mockPlayer(playerId)
        val districtInfo = mockDistrictInfo(districtId, currentStockValue = 40)
        val ownedStock = mockPlayerStock(quantity = 20)
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(gameDistrictInformationDao.findByGameAndDistrict(gameId, districtId))
            .willReturn(districtInfo)
        given(playerStockDao.find(playerId, districtInfo.id.value)).willReturn(ownedStock)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        service.sellStock(gameId, playerId, districtId, 10)

        // The net worth check (see GameSimulationService.endGameIfNetWorthReached) now
        // legitimately reads owned shops on every stock trade -- the meaningful assertion here is
        // that the district's own stock price was never touched. setCurrentStockValue takes a
        // non-null Uuid, so an eq()/anyInt() matcher pair returns null for it and NPEs against
        // Kotlin's null-check on that parameter -- checking recorded invocations directly
        // sidesteps invoking the real method at all.
        val stockPriceCalls =
            mockingDetails(gameDistrictInformationDao).invocations.filter {
                it.method.name == "setCurrentStockValue"
            }
        assertThat(stockPriceCalls).isEmpty()
    }

    @Test
    fun `skipStockTrade ends the turn without buying or selling anything, when no movement is left`() {
        val playerId = Uuid.random()
        val bankSpaceId = Uuid.random()
        val game =
            mockGame(
                turnOrder = listOf(playerId),
                turnNumber = 0,
                currentMovementPoints = 0,
                pendingStockTradeSpaceId = bankSpaceId,
            )
        val player = mockPlayer(playerId)
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        val result = service.skipStockTrade(gameId, playerId)

        assertThat(result.getOrNull())
            .containsExactly(
                GameSimulationService.TurnEvent.TurnEnded(playerId, 0, gameOver = false),
                GameSimulationService.TurnEvent.TurnStarted(playerId, 1),
            )
        verifyNoInteractions(playerStockDao)
    }

    @Test
    fun `skipStockTrade resumes movement with whatever was left, rather than ending the turn early`() {
        val playerId = Uuid.random()
        val bankSpaceId = Uuid.random()
        val nextSpaceId = Uuid.random()
        val game =
            mockGame(
                turnOrder = listOf(playerId),
                turnNumber = 0,
                currentMovementPoints = 1,
                pendingStockTradeSpaceId = bankSpaceId,
            )
        val player = mockPlayer(playerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = bankSpaceId)
        val board = mockBoard()
        val boardGraph =
            BoardGraph(
                board = board,
                spaces = emptyList(),
                paths = listOf(mockPath(bankSpaceId, nextSpaceId, 0)),
            )
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        val result = service.skipStockTrade(gameId, playerId)

        assertThat(result.getOrNull())
            .containsExactly(
                GameSimulationService.TurnEvent.Moved(playerId, 0, bankSpaceId, nextSpaceId, 0),
                GameSimulationService.TurnEvent.TurnEnded(playerId, 0, gameOver = false),
                GameSimulationService.TurnEvent.TurnStarted(playerId, 1),
            )
    }

    @Test
    fun `a computer player automatically skips stock trading (no policy implemented yet), without pausing`() {
        val computerId = Uuid.random()
        val otherPlayerId = Uuid.random()
        val spaceId = Uuid.random()
        val bankSpaceId = Uuid.random()
        val districtId = Uuid.random()
        val turnOrder = listOf(computerId, otherPlayerId)
        val game = mockGame(turnOrder = turnOrder, turnNumber = 0, maxTurns = 10)
        val computer = mockPlayer(computerId, userId = null)
        val otherPlayer = mockPlayer(otherPlayerId)
        val computerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceId)
        val board = mockBoard()
        val bankSpace = mockSpace(bankSpaceId, SpaceType.BANK)
        val boardGraph =
            BoardGraph(
                board = board,
                spaces = listOf(bankSpace),
                paths = listOf(mockPath(spaceId, bankSpaceId, 0)),
            )
        val districtInfo = mockDistrictInfo(districtId, currentStockValue = 200)
        val advancedGame = mockGame(turnOrder = turnOrder, turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(computer, otherPlayer))
        given(playerDao.findState(computerId)).willReturn(computerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(1)
        given(gameDistrictInformationDao.findAllByGame(gameId)).willReturn(listOf(districtInfo))
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        val result = service.rollDice(gameId, computerId)

        assertThat(result.getOrNull())
            .containsExactly(
                GameSimulationService.TurnEvent.DiceRolled(computerId, 1),
                GameSimulationService.TurnEvent.Moved(computerId, 0, spaceId, bankSpaceId, 0),
                GameSimulationService.TurnEvent.TurnEnded(computerId, 0, gameOver = false),
                GameSimulationService.TurnEvent.TurnStarted(otherPlayerId, 1),
            )
        verify(computerPlayer)
            .chooseStockTrade(
                listOf(StockTradeOffer(districtId, districtInfo.id.value, 200, 0)),
                1000,
            )
        verify(gameDao, never()).setPendingStockTradeSpace(gameId, bankSpaceId)
    }

    // --- choosePath ---

    @Test
    fun `choosePath fails when the game doesn't exist`() {
        given(gameDao.findById(gameId)).willReturn(null)

        val result = service.choosePath(gameId, Uuid.random(), Uuid.random())

        assertThat(result.exceptionOrNull()).isInstanceOf(GameNotFoundException::class.java)
    }

    @Test
    fun `choosePath fails when it isn't the given player's turn`() {
        val currentPlayerId = Uuid.random()
        val otherPlayerId = Uuid.random()
        val game = mockGame(turnOrder = listOf(currentPlayerId, otherPlayerId))
        given(gameDao.findById(gameId)).willReturn(game)

        val result = service.choosePath(gameId, otherPlayerId, Uuid.random())

        assertThat(result.exceptionOrNull()).isInstanceOf(InvalidTurnException::class.java)
    }

    @Test
    fun `choosePath fails when the player hasn't rolled the dice yet`() {
        val playerId = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId), currentMovementPoints = null)
        given(gameDao.findById(gameId)).willReturn(game)

        val result = service.choosePath(gameId, playerId, Uuid.random())

        assertThat(result.exceptionOrNull()).isInstanceOf(InvalidTurnException::class.java)
    }

    @Test
    fun `choosePath fails when a stock trade decision is pending`() {
        val playerId = Uuid.random()
        val bankSpaceId = Uuid.random()
        val game =
            mockGame(
                turnOrder = listOf(playerId),
                currentMovementPoints = 1,
                pendingStockTradeSpaceId = bankSpaceId,
            )
        given(gameDao.findById(gameId)).willReturn(game)

        val result = service.choosePath(gameId, playerId, Uuid.random())

        assertThat(result.exceptionOrNull()).isInstanceOf(InvalidTurnException::class.java)
    }

    @Test
    fun `choosePath fails when the chosen space isn't a valid option`() {
        val playerId = Uuid.random()
        val spaceId = Uuid.random()
        val validChoice = Uuid.random()
        val invalidChoice = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId), currentMovementPoints = 1)
        val player = mockPlayer(playerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceId)
        val board = mockBoard()
        val boardGraph =
            BoardGraph(
                board = board,
                spaces = emptyList(),
                paths = listOf(mockPath(spaceId, validChoice, 0)),
            )
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)

        val result = service.choosePath(gameId, playerId, invalidChoice)

        assertThat(result.exceptionOrNull()).isInstanceOf(InvalidTurnException::class.java)
    }

    @Test
    fun `choosePath moves onto the chosen branch and ends the turn once movement is exhausted`() {
        val playerId = Uuid.random()
        val spaceId = Uuid.random()
        val branchC = Uuid.random()
        val branchD = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId), turnNumber = 0, currentMovementPoints = 1)
        val player = mockPlayer(playerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceId)
        val board = mockBoard()
        val boardGraph =
            BoardGraph(
                board = board,
                spaces = emptyList(),
                paths = listOf(mockPath(spaceId, branchC, 0), mockPath(spaceId, branchD, 1)),
            )
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        val result = service.choosePath(gameId, playerId, branchD)

        val events = result.getOrNull()
        assertThat(events)
            .containsExactly(
                GameSimulationService.TurnEvent.Moved(playerId, 0, spaceId, branchD, 0),
                GameSimulationService.TurnEvent.TurnEnded(playerId, 0, gameOver = false),
                GameSimulationService.TurnEvent.TurnStarted(playerId, 1),
            )
        verify(playerDao).updatePosition(playerId, branchD)
    }

    @Test
    fun `choosePath keeps moving with any movement left over after the choice`() {
        val playerId = Uuid.random()
        val spaceId = Uuid.random()
        val branchC = Uuid.random()
        val afterBranch = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId), turnNumber = 0, currentMovementPoints = 2)
        val player = mockPlayer(playerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceId)
        val board = mockBoard()
        val boardGraph =
            BoardGraph(
                board = board,
                spaces = emptyList(),
                paths =
                    listOf(
                        mockPath(spaceId, branchC, 0),
                        mockPath(spaceId, Uuid.random(), 1),
                        mockPath(branchC, afterBranch, 0),
                    ),
            )
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        val result = service.choosePath(gameId, playerId, branchC)

        val events = result.getOrNull()
        assertThat(events)
            .containsExactly(
                GameSimulationService.TurnEvent.Moved(playerId, 0, spaceId, branchC, 1),
                GameSimulationService.TurnEvent.Moved(playerId, 0, branchC, afterBranch, 0),
                GameSimulationService.TurnEvent.TurnEnded(playerId, 0, gameOver = false),
                GameSimulationService.TurnEvent.TurnStarted(playerId, 1),
            )
    }

    @Test
    fun `choosePath chains into the next computer player's turn once the human's turn ends`() {
        val humanId = Uuid.random()
        val computerId = Uuid.random()
        val spaceId = Uuid.random()
        val branchC = Uuid.random()
        val computerSpaceId = Uuid.random()
        val computerNextSpaceId = Uuid.random()
        val turnOrder = listOf(humanId, computerId)
        val game = mockGame(turnOrder = turnOrder, turnNumber = 0, currentMovementPoints = 1)
        val human = mockPlayer(humanId)
        val computer = mockPlayer(computerId, userId = null)
        val humanState = mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceId)
        val computerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = computerSpaceId)
        val board = mockBoard()
        val computerPath = mockPath(computerSpaceId, computerNextSpaceId, 0)
        val boardGraph =
            BoardGraph(
                board = board,
                spaces = emptyList(),
                paths = listOf(mockPath(spaceId, branchC, 0), computerPath),
            )
        val afterHuman = mockGame(turnOrder = turnOrder, turnNumber = 1)
        val afterComputer = mockGame(turnOrder = turnOrder, turnNumber = 2)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(human, computer))
        given(playerDao.findState(humanId)).willReturn(humanState)
        given(playerDao.findState(computerId)).willReturn(computerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(1)
        given(gameDao.advanceTurn(gameId)).willReturn(afterHuman, afterComputer)

        val result = service.choosePath(gameId, humanId, branchC)

        val events = result.getOrNull()
        assertThat(events).isNotNull()
        assertThat(
                events!!.filterIsInstance<GameSimulationService.TurnEvent.DiceRolled>().map {
                    it.playerId
                }
            )
            .containsExactly(computerId)
        val movedEvents = events.filterIsInstance<GameSimulationService.TurnEvent.Moved>()
        assertThat(movedEvents.map { it.playerId }).containsExactly(humanId, computerId)
        assertThat(movedEvents.last().toSpaceId).isEqualTo(computerNextSpaceId)
    }

    // --- markReady seeds shop info ---

    @Test
    fun `markReady seeds per-game shop information once the game actually starts`() {
        val playerId = Uuid.random()
        val game = mockGame()
        val player = mockPlayer(playerId)
        val board = mockBoard()
        val boardGraph = BoardGraph(board = board, spaces = emptyList(), paths = emptyList())
        val startedGame = mockGame(turnOrder = listOf(playerId))
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(gameDao.startGame(gameId, listOf(playerId))).willReturn(startedGame)
        given(boardDao.findById(boardId)).willReturn(boardGraph)

        service.markReady(gameId, playerId)

        verify(gameShopInformationDao).seedForGame(gameId, boardGraph)
    }

    @Test
    fun `markReady also seeds per-game district stock information, from the just-seeded shops`() {
        val playerId = Uuid.random()
        val game = mockGame()
        val player = mockPlayer(playerId)
        val board = mockBoard()
        val boardGraph = BoardGraph(board = board, spaces = emptyList(), paths = emptyList())
        val startedGame = mockGame(turnOrder = listOf(playerId))
        val seededShops = listOf(mock(GameShopInformation::class.java))
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(gameDao.startGame(gameId, listOf(playerId))).willReturn(startedGame)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(gameShopInformationDao.seedForGame(gameId, boardGraph)).willReturn(seededShops)

        service.markReady(gameId, playerId)

        verify(gameDistrictInformationDao).seedForGame(gameId, boardGraph, seededShops)
    }

    // --- shop purchases ---

    @Test
    fun `rollDice pauses and offers a purchase when movement ends on an unowned shop, for a human player`() {
        val playerId = Uuid.random()
        val spaceId = Uuid.random()
        val shopSpaceId = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId))
        val player = mockPlayer(playerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceId)
        val board = mockBoard()
        val boardGraph =
            BoardGraph(
                board = board,
                spaces = emptyList(),
                paths = listOf(mockPath(spaceId, shopSpaceId, 0)),
            )
        val shop = mockShop(spaceId = shopSpaceId, currentValue = 250)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(1)
        given(gameShopInformationDao.findByGameAndSpace(gameId, shopSpaceId)).willReturn(shop)

        val result = service.rollDice(gameId, playerId)

        val events = result.getOrNull()
        assertThat(events)
            .containsExactly(
                GameSimulationService.TurnEvent.DiceRolled(playerId, 1),
                GameSimulationService.TurnEvent.Moved(playerId, 0, spaceId, shopSpaceId, 0),
                GameSimulationService.TurnEvent.ShopPurchaseAvailable(playerId, shopSpaceId, 250),
            )
        verify(gameDao).setMovementPoints(gameId, 0)
        verify(gameDao, never()).advanceTurn(gameId)
    }

    @Test
    fun `rollDice ends the turn normally when landing on an already-owned shop`() {
        val playerId = Uuid.random()
        val ownerId = Uuid.random()
        val spaceId = Uuid.random()
        val shopSpaceId = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId))
        val player = mockPlayer(playerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceId)
        val board = mockBoard()
        val boardGraph =
            BoardGraph(
                board = board,
                spaces = emptyList(),
                paths = listOf(mockPath(spaceId, shopSpaceId, 0)),
            )
        val shop = mockShop(spaceId = shopSpaceId, currentValue = 250, ownerId = ownerId)
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(1)
        given(gameShopInformationDao.findByGameAndSpace(gameId, shopSpaceId)).willReturn(shop)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        val result = service.rollDice(gameId, playerId)

        assertThat(result.getOrNull())
            .containsExactly(
                GameSimulationService.TurnEvent.DiceRolled(playerId, 1),
                GameSimulationService.TurnEvent.Moved(playerId, 0, spaceId, shopSpaceId, 0),
                GameSimulationService.TurnEvent.TurnEnded(playerId, 0, gameOver = false),
                GameSimulationService.TurnEvent.TurnStarted(playerId, 1),
            )
    }

    @Test
    fun `buyShop fails when there's no purchase decision pending`() {
        val playerId = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId), currentMovementPoints = null)
        given(gameDao.findById(gameId)).willReturn(game)

        val result = service.buyShop(gameId, playerId)

        assertThat(result.exceptionOrNull()).isInstanceOf(InvalidTurnException::class.java)
    }

    @Test
    fun `buyShop fails when the player is mid-movement with a branch choice pending, not a purchase`() {
        val playerId = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId), currentMovementPoints = 2)
        given(gameDao.findById(gameId)).willReturn(game)

        val result = service.buyShop(gameId, playerId)

        assertThat(result.exceptionOrNull()).isInstanceOf(InvalidTurnException::class.java)
    }

    @Test
    fun `buyShop deducts the price from the player's gold, sets ownership, and ends the turn`() {
        val playerId = Uuid.random()
        val spaceId = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId), turnNumber = 0, currentMovementPoints = 0)
        val player = mockPlayer(playerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceId)
        val shop = mockShop(spaceId = spaceId, currentValue = 150)
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(gameShopInformationDao.findByGameAndSpace(gameId, spaceId)).willReturn(shop)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        val result = service.buyShop(gameId, playerId)

        assertThat(result.getOrNull())
            .containsExactly(
                GameSimulationService.TurnEvent.ShopPurchased(playerId, spaceId, 150),
                GameSimulationService.TurnEvent.TurnEnded(playerId, 0, gameOver = false),
                GameSimulationService.TurnEvent.TurnStarted(playerId, 1),
            )
        verify(playerDao).adjustGold(playerId, -150)
        verify(gameShopInformationDao).setOwner(shop.id.value, playerId)
    }

    @Test
    fun `buyShop ends the game early once the buyer's net worth reaches the game's target net worth`() {
        val playerId = Uuid.random()
        val spaceId = Uuid.random()
        val game =
            mockGame(
                turnOrder = listOf(playerId),
                turnNumber = 0,
                currentMovementPoints = 0,
                targetNetWorth = 6000,
            )
        val player = mockPlayer(playerId)
        val playerState =
            mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceId, currentGold = 10000)
        val shop = mockShop(spaceId = spaceId, currentValue = 6000)
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(gameShopInformationDao.findByGameAndSpace(gameId, spaceId)).willReturn(shop)
        // Reflects the shop the buyer is about to own -- findOwnedByPlayer is a separate mock
        // from setOwner and isn't wired to it here, so it's stubbed directly, exactly as other
        // tests above already stub it for the BANK promotion payout.
        given(gameShopInformationDao.findOwnedByPlayer(gameId, playerId)).willReturn(listOf(shop))
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        service.buyShop(gameId, playerId)

        verify(gameDao).endGameEarly(gameId)
    }

    @Test
    fun `buyShop does not end the game early when net worth stays below the target`() {
        val playerId = Uuid.random()
        val spaceId = Uuid.random()
        val game =
            mockGame(
                turnOrder = listOf(playerId),
                turnNumber = 0,
                currentMovementPoints = 0,
                targetNetWorth = 6000,
            )
        val player = mockPlayer(playerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceId)
        val shop = mockShop(spaceId = spaceId, currentValue = 150)
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(gameShopInformationDao.findByGameAndSpace(gameId, spaceId)).willReturn(shop)
        given(gameShopInformationDao.findOwnedByPlayer(gameId, playerId)).willReturn(listOf(shop))
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        service.buyShop(gameId, playerId)

        verify(gameDao, never()).endGameEarly(gameId)
    }

    @Test
    fun `buyShop fails when the player can't afford the shop's price, leaving it unowned and their gold untouched`() {
        val playerId = Uuid.random()
        val spaceId = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId), turnNumber = 0, currentMovementPoints = 0)
        val player = mockPlayer(playerId)
        val playerState =
            mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceId, currentGold = 50)
        val shop = mockShop(spaceId = spaceId, currentValue = 150)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(gameShopInformationDao.findByGameAndSpace(gameId, spaceId)).willReturn(shop)

        val result = service.buyShop(gameId, playerId)

        assertThat(result.exceptionOrNull()).isInstanceOf(InvalidTurnException::class.java)
        verify(gameShopInformationDao, never()).setOwner(shop.id.value, playerId)
        verify(playerDao, never()).adjustGold(playerId, -150)
        verify(gameDao, never()).advanceTurn(gameId)
    }

    @Test
    fun `buyShop recalculates every owned shop in the district once the player's count there reaches 2`() {
        val playerId = Uuid.random()
        val spaceId = Uuid.random()
        val otherSpaceId = Uuid.random()
        val districtId = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId), turnNumber = 0, currentMovementPoints = 0)
        val player = mockPlayer(playerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceId)
        val newShop = mockShop(spaceId = spaceId, currentValue = 100, districtId = districtId)
        val existingShop =
            mockShop(
                spaceId = otherSpaceId,
                currentValue = 200,
                ownerId = playerId,
                districtId = districtId,
            )
        val progression = mock(DistrictValueProgression::class.java)
        lenient().`when`(progression.existingShopBoostPercentage).thenReturn(BigDecimal("0.1000"))
        lenient().`when`(progression.newShopBoostPercentage).thenReturn(BigDecimal("0.2000"))
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(gameShopInformationDao.findByGameAndSpace(gameId, spaceId)).willReturn(newShop)
        given(
                gameShopInformationDao.findOwnedByPlayerInDistrict(
                    gameId,
                    playerId,
                    newShop.districtId!!,
                )
            )
            .willReturn(listOf(newShop, existingShop))
        given(boardDao.findDistrictValueProgression(newShop.districtId!!, 2))
            .willReturn(progression)
        given(gameShopInformationDao.findByGameAndDistrict(gameId, newShop.districtId!!))
            .willReturn(listOf(newShop, existingShop))
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        val result = service.buyShop(gameId, playerId)

        val events = result.getOrNull()
        val recalculated =
            events
                ?.filterIsInstance<GameSimulationService.TurnEvent.DistrictValuesRecalculated>()
                ?.single()
        assertThat(recalculated).isNotNull()
        assertThat(recalculated!!.districtId).isEqualTo(districtId)
        // newShop just bought: 100 boosted by newShopBoostPercentage (0.2000) -> 120
        assertThat(recalculated.newValuesBySpaceId[spaceId]).isEqualTo(120)
        // existingShop already owned: 200 boosted by existingShopBoostPercentage (0.1000) -> 220
        assertThat(recalculated.newValuesBySpaceId[otherSpaceId]).isEqualTo(220)
        verify(gameShopInformationDao).setCurrentValue(newShop.id.value, 120)
        verify(gameShopInformationDao).setCurrentValue(existingShop.id.value, 220)
        // The district's stock is re-averaged from every shop in it (re-read after the boosts
        // above), not just the two that were just boosted.
        verify(gameDistrictInformationDao)
            .recalculateCurrentStockValue(
                gameId,
                newShop.districtId!!,
                listOf(newShop, existingShop),
            )
    }

    @Test
    fun `buyShop does not recalculate district values when this is still the player's only shop there`() {
        val playerId = Uuid.random()
        val spaceId = Uuid.random()
        val districtId = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId), turnNumber = 0, currentMovementPoints = 0)
        val player = mockPlayer(playerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceId)
        val shop = mockShop(spaceId = spaceId, currentValue = 100, districtId = districtId)
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(gameShopInformationDao.findByGameAndSpace(gameId, spaceId)).willReturn(shop)
        given(
                gameShopInformationDao.findOwnedByPlayerInDistrict(
                    gameId,
                    playerId,
                    shop.districtId!!,
                )
            )
            .willReturn(listOf(shop))
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        val result = service.buyShop(gameId, playerId)

        assertThat(result.getOrNull())
            .containsExactly(
                GameSimulationService.TurnEvent.ShopPurchased(playerId, spaceId, 100),
                GameSimulationService.TurnEvent.TurnEnded(playerId, 0, gameOver = false),
                GameSimulationService.TurnEvent.TurnStarted(playerId, 1),
            )
        // Nothing boosted, so nothing for the district's stock value to be re-averaged from.
        verifyNoInteractions(gameDistrictInformationDao)
    }

    @Test
    fun `declineShopPurchase ends the turn without buying anything`() {
        val playerId = Uuid.random()
        val spaceId = Uuid.random()
        val game = mockGame(turnOrder = listOf(playerId), turnNumber = 0, currentMovementPoints = 0)
        val player = mockPlayer(playerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceId)
        val shop = mockShop(spaceId = spaceId, currentValue = 150)
        val advancedGame = mockGame(turnOrder = listOf(playerId), turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(player))
        given(playerDao.findState(playerId)).willReturn(playerState)
        given(gameShopInformationDao.findByGameAndSpace(gameId, spaceId)).willReturn(shop)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        val result = service.declineShopPurchase(gameId, playerId)

        assertThat(result.getOrNull())
            .containsExactly(
                GameSimulationService.TurnEvent.TurnEnded(playerId, 0, gameOver = false),
                GameSimulationService.TurnEvent.TurnStarted(playerId, 1),
            )
        verify(gameShopInformationDao, never()).setOwner(shop.id.value, playerId)
    }

    @Test
    fun `a computer player automatically buys an unowned shop it lands on, without pausing`() {
        val computerId = Uuid.random()
        val otherPlayerId = Uuid.random()
        val spaceId = Uuid.random()
        val shopSpaceId = Uuid.random()
        val turnOrder = listOf(computerId, otherPlayerId)
        val game = mockGame(turnOrder = turnOrder, turnNumber = 0, maxTurns = 10)
        val computer = mockPlayer(computerId, userId = null)
        val otherPlayer = mockPlayer(otherPlayerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceId)
        val board = mockBoard()
        val boardGraph =
            BoardGraph(
                board = board,
                spaces = emptyList(),
                paths = listOf(mockPath(spaceId, shopSpaceId, 0)),
            )
        val shop = mockShop(spaceId = shopSpaceId, currentValue = 100)
        val advancedGame = mockGame(turnOrder = turnOrder, turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(computer, otherPlayer))
        given(playerDao.findState(computerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(1)
        given(gameShopInformationDao.findByGameAndSpace(gameId, shopSpaceId)).willReturn(shop)
        given(computerPlayer.shouldBuyShop(shop, 1000)).willReturn(true)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        val result = service.rollDice(gameId, computerId)

        assertThat(result.getOrNull())
            .containsExactly(
                GameSimulationService.TurnEvent.DiceRolled(computerId, 1),
                GameSimulationService.TurnEvent.Moved(computerId, 0, spaceId, shopSpaceId, 0),
                GameSimulationService.TurnEvent.ShopPurchased(computerId, shopSpaceId, 100),
                GameSimulationService.TurnEvent.TurnEnded(computerId, 0, gameOver = false),
                GameSimulationService.TurnEvent.TurnStarted(otherPlayerId, 1),
            )
        verify(playerDao).adjustGold(computerId, -100)
        verify(gameDao, never()).setMovementPoints(gameId, 0)
    }

    @Test
    fun `a computer player can decline to buy a shop it lands on`() {
        val computerId = Uuid.random()
        // A second, human player after this one in turn order -- same reason as
        // `rollDice lets a computer player pick a branch randomly instead of pausing`
        // above: with a turn order of just the one computer, ending its turn hands
        // play right back to that same computer again (and again), and this test's
        // fixed, non-incrementing advanceTurn stub would make that loop forever.
        val otherPlayerId = Uuid.random()
        val spaceId = Uuid.random()
        val shopSpaceId = Uuid.random()
        val turnOrder = listOf(computerId, otherPlayerId)
        val game = mockGame(turnOrder = turnOrder, turnNumber = 0, maxTurns = 10)
        val computer = mockPlayer(computerId, userId = null)
        val otherPlayer = mockPlayer(otherPlayerId)
        val playerState = mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceId)
        val board = mockBoard()
        val boardGraph =
            BoardGraph(
                board = board,
                spaces = emptyList(),
                paths = listOf(mockPath(spaceId, shopSpaceId, 0)),
            )
        val shop = mockShop(spaceId = shopSpaceId, currentValue = 100)
        val advancedGame = mockGame(turnOrder = turnOrder, turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(computer, otherPlayer))
        given(playerDao.findState(computerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(1)
        given(gameShopInformationDao.findByGameAndSpace(gameId, shopSpaceId)).willReturn(shop)
        given(computerPlayer.shouldBuyShop(shop, 1000)).willReturn(false)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        val result = service.rollDice(gameId, computerId)

        assertThat(result.getOrNull())
            .containsExactly(
                GameSimulationService.TurnEvent.DiceRolled(computerId, 1),
                GameSimulationService.TurnEvent.Moved(computerId, 0, spaceId, shopSpaceId, 0),
                GameSimulationService.TurnEvent.TurnEnded(computerId, 0, gameOver = false),
                GameSimulationService.TurnEvent.TurnStarted(otherPlayerId, 1),
            )
        verify(gameShopInformationDao, never()).setOwner(shop.id.value, computerId)
    }

    @Test
    fun `a computer player's shouldBuyShop is given their actual current gold, and a 'no' is respected`() {
        // currentGold here is well more than enough to afford the shop, so there's nothing
        // ambiguous about *why* no purchase happens -- it's purely because shouldBuyShop said no.
        val computerId = Uuid.random()
        val otherPlayerId = Uuid.random()
        val spaceId = Uuid.random()
        val shopSpaceId = Uuid.random()
        val turnOrder = listOf(computerId, otherPlayerId)
        val game = mockGame(turnOrder = turnOrder, turnNumber = 0, maxTurns = 10)
        val computer = mockPlayer(computerId, userId = null)
        val otherPlayer = mockPlayer(otherPlayerId)
        val playerState =
            mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceId, currentGold = 1000)
        val board = mockBoard()
        val boardGraph =
            BoardGraph(
                board = board,
                spaces = emptyList(),
                paths = listOf(mockPath(spaceId, shopSpaceId, 0)),
            )
        val shop = mockShop(spaceId = shopSpaceId, currentValue = 100)
        val advancedGame = mockGame(turnOrder = turnOrder, turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(computer, otherPlayer))
        given(playerDao.findState(computerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(1)
        given(gameShopInformationDao.findByGameAndSpace(gameId, shopSpaceId)).willReturn(shop)
        given(computerPlayer.shouldBuyShop(shop, 1000)).willReturn(false)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        val result = service.rollDice(gameId, computerId)

        assertThat(result.getOrNull())
            .containsExactly(
                GameSimulationService.TurnEvent.DiceRolled(computerId, 1),
                GameSimulationService.TurnEvent.Moved(computerId, 0, spaceId, shopSpaceId, 0),
                GameSimulationService.TurnEvent.TurnEnded(computerId, 0, gameOver = false),
                GameSimulationService.TurnEvent.TurnStarted(otherPlayerId, 1),
            )
        verify(computerPlayer).shouldBuyShop(shop, 1000)
        verify(gameShopInformationDao, never()).setOwner(shop.id.value, computerId)
    }

    @Test
    fun `GameSimulationService still blocks a computer's purchase it can't afford, even when ComputerPlayer says yes`() {
        val computerId = Uuid.random()
        val otherPlayerId = Uuid.random()
        val spaceId = Uuid.random()
        val shopSpaceId = Uuid.random()
        val turnOrder = listOf(computerId, otherPlayerId)
        val game = mockGame(turnOrder = turnOrder, turnNumber = 0, maxTurns = 10)
        val computer = mockPlayer(computerId, userId = null)
        val otherPlayer = mockPlayer(otherPlayerId)
        val playerState =
            mockPlayerState(PlayerStatus.READY, currentSpaceId = spaceId, currentGold = 50)
        val board = mockBoard()
        val boardGraph =
            BoardGraph(
                board = board,
                spaces = emptyList(),
                paths = listOf(mockPath(spaceId, shopSpaceId, 0)),
            )
        val shop = mockShop(spaceId = shopSpaceId, currentValue = 100)
        val advancedGame = mockGame(turnOrder = turnOrder, turnNumber = 1)
        given(gameDao.findById(gameId)).willReturn(game)
        given(playerDao.findByGameId(gameId)).willReturn(listOf(computer, otherPlayer))
        given(playerDao.findState(computerId)).willReturn(playerState)
        given(boardDao.findById(boardId)).willReturn(boardGraph)
        given(dice.roll()).willReturn(1)
        given(gameShopInformationDao.findByGameAndSpace(gameId, shopSpaceId)).willReturn(shop)
        // Deliberately "misbehaving" stub -- says yes despite being unaffordable, to prove
        // GameSimulationService enforces the floor itself rather than trusting this blindly.
        given(computerPlayer.shouldBuyShop(shop, 50)).willReturn(true)
        given(gameDao.advanceTurn(gameId)).willReturn(advancedGame)

        val result = service.rollDice(gameId, computerId)

        assertThat(result.getOrNull())
            .containsExactly(
                GameSimulationService.TurnEvent.DiceRolled(computerId, 1),
                GameSimulationService.TurnEvent.Moved(computerId, 0, spaceId, shopSpaceId, 0),
                GameSimulationService.TurnEvent.TurnEnded(computerId, 0, gameOver = false),
                GameSimulationService.TurnEvent.TurnStarted(otherPlayerId, 1),
            )
        verify(gameShopInformationDao, never()).setOwner(shop.id.value, computerId)
        verify(playerDao, never()).adjustGold(computerId, -100)
    }
}
