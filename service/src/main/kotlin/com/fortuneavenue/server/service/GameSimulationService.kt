package com.fortuneavenue.server.service

import com.fortuneavenue.server.dao.BoardDao
import com.fortuneavenue.server.dao.GameDao
import com.fortuneavenue.server.dao.GameDistrictInformationDao
import com.fortuneavenue.server.dao.GameShopInformationDao
import com.fortuneavenue.server.dao.PlayerDao
import com.fortuneavenue.server.dao.PlayerStockDao
import com.fortuneavenue.server.models.board.db.BoardGraph
import com.fortuneavenue.server.models.board.db.BoardPath
import com.fortuneavenue.server.models.board.db.GameDistrictInformation
import com.fortuneavenue.server.models.board.db.GameShopInformation
import com.fortuneavenue.server.models.board.db.SpaceType
import com.fortuneavenue.server.models.game.db.Game
import com.fortuneavenue.server.models.player.db.Player
import com.fortuneavenue.server.models.player.db.PlayerStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.springframework.stereotype.Service

/**
 * Owns the actual flow of a game: readying up, deciding turn order once everyone's ready, and
 * playing out turns.
 *
 * A turn starts with a die roll, which sets how many spaces the current player has left to move.
 * They're moved forward automatically, one space at a time, decrementing remaining movement, for as
 * long as the space they're on only has one path out of it. The moment they land somewhere with
 * more than one outgoing path, movement pauses: a human player has to choose which branch to take
 * (see [choosePath]) before moving continues, while a computer player picks right away (see
 * [ComputerPlayer]) and keeps going without ever pausing. If movement instead runs out on an
 * unowned SHOP space, a human player is offered the chance to buy it (see
 * [buyShop]/[declineShopPurchase]) before the turn actually ends, while a computer player decides
 * whether it wants to right away, given its actual current gold (again see
 * [ComputerPlayer.shouldBuyShop]). Either way, a purchase always requires enough gold on hand up
 * front -- [buyShop] fails outright for a human short on gold, and a computer player wanting a shop
 * it can't afford is simply treated the same as it not wanting one. Landing on a SHOP owned by
 * another player instead charges a toll straight to them -- see [tollAmount] for the exact
 * formula (see [payToll]) -- for both a human and a computer player, with no decision
 * to make either way; landing on a shop the mover already owns themselves is a no-op. Gold can go
 * negative from a toll or a purchase alike, since neither checks the payer's balance first. The
 * turn ends once movement
 * reaches zero, at which point play moves to the next player in turn order. This is announced with
 * a [TurnEvent.TurnStarted] the moment that next player is a human, since nothing else is going to
 * happen until they roll themselves. The game ends once turnNumber reaches maxTurns -- or the
 * moment any player's net worth (gold on hand, plus every shop they own, plus the current value of
 * every stock they hold -- see [netWorth]) reaches or exceeds the game's targetNetWorth, whichever
 * happens first (see [endGameIfNetWorthReached]).
 *
 * Every space a player is moved onto along the way, whether just passed through mid-move or where
 * they end up, is also checked for a suit (HEART/DIAMOND/SPADE/CLUB, see SpaceType): landing on or
 * passing one picks it up for that player (see PlayerDao.addHeldSuit), announced with a
 * [TurnEvent.SuitPickedUp] the first time, and silently ignored every time after.
 *
 * The same is true of a BANK space, but the other direction: passing or landing on one while
 * currently holding all 4 suits triggers a promotion (see [checkPromotion]) -- their held suits are
 * cleared (see PlayerDao.clearHeldSuits), their promotion count goes up by one for next time (see
 * PlayerDao.incrementPromotionCount), and they're paid out board.baseSalary +
 * (board.promotionBonus * however many times they'd already been promoted this game) + the current
 * value of every shop they own, announced with a [TurnEvent.Promoted]. A BANK space is otherwise a
 * no-op for promotion purposes -- nothing happens visiting one without every suit in hand.
 *
 * Independent of promotion, a BANK space also always offers a stock trade decision (see
 * [checkStockTrade]) if any district has stock to trade in this game: a human player pauses there
 * (a [TurnEvent.StockTradingAvailable] naming every tradeable district) until
 * [buyStock]/[sellStock]/[skipStockTrade] resolves it -- unlike a shop purchase, this pause can
 * happen with movement still left, since a BANK space can be merely passed through mid-move, not
 * just landed on. A computer player instead decides immediately via
 * [ComputerPlayer.chooseStockTrade] (no real policy yet) and never pauses.
 *
 * A player with no [com.fortuneavenue.server.models.player.db.Player.userId] is a computer opponent
 * -- there's nobody connected who could ready it up or take its turns, so this service does that on
 * its behalf: computer players are auto-readied once every human is, and their turns are played out
 * automatically (roll and all) as soon as it's their turn
 */
@Service
class GameSimulationService(
    private val gameDao: GameDao,
    private val playerDao: PlayerDao,
    private val boardDao: BoardDao,
    private val gameShopInformationDao: GameShopInformationDao,
    private val gameDistrictInformationDao: GameDistrictInformationDao,
    private val playerStockDao: PlayerStockDao,
    private val dice: Dice,
    private val computerPlayer: ComputerPlayer,
) {

    // Serializes every action that reads then writes a game's turn state -- markReady (racing to
    // start the game / seed its shop+district info), and every turn-mutating action below
    // (rollDice/choosePath/buyShop/declineShopPurchase/buyStock/sellStock/skipStockTrade). Each
    // websocket message is handled on its own thread, and none of GameDao/PlayerDao/
    // GameShopInformationDao's individual reads-then-writes are atomic across threads on their
    // own -- without this, two messages for the same game arriving close together (a double
    // click, a retried send, two tabs open as the same player) can both read the same "shop still
    // unowned" state before either commits, and both end up buying it. Keyed per game so unrelated
    // games never contend with each other. In-memory only, which is fine: like
    // GameWebSocketHandler's session bookkeeping, this only ever needs to coordinate within a
    // single server instance (see GameWebSocketHandler's class doc).
    private val gameLocks = ConcurrentHashMap<Uuid, Any>()

    /** Runs [block] holding [gameId]'s lock -- see [gameLocks]. */
    private fun <T> withGameLock(gameId: Uuid, block: () -> T): T =
        synchronized(gameLocks.computeIfAbsent(gameId) { Any() }, block)

    sealed interface ReadyOutcome {
        /** Marked ready, but not every player is (or the game already started). */
        data object Waiting : ReadyOutcome

        /**
         * This was the last player needed -- turn order's been decided and the game has started.
         * [openingTurnEvents] is whatever happened automatically because one or more computer
         * players led that turn order (their full turns played out, one after another), followed by
         * a [TurnEvent.TurnStarted] for whichever human ends up first in line once they're done --
         * or just that one event, if a human leads turn order to begin with.
         */
        data class GameStarted(
            val turnOrder: List<Uuid>,
            val openingTurnEvents: List<TurnEvent> = emptyList(),
        ) : ReadyOutcome
    }

    /** One outgoing path a player can choose when paused at a branch. */
    data class PathOption(val toSpaceId: Uuid, val branchOrder: Int)

    /**
     * Everything that can happen in the course of rolling, moving, and (eventually) ending a turn.
     */
    sealed interface TurnEvent {
        val playerId: Uuid

        data class DiceRolled(override val playerId: Uuid, val roll: Int) : TurnEvent

        data class Moved(
            override val playerId: Uuid,
            val turnNumber: Int,
            val fromSpaceId: Uuid,
            val toSpaceId: Uuid,
            val movementPointsRemaining: Int,
        ) : TurnEvent

        /**
         * [playerId] passed or landed on [spaceId], a suit space (HEART/DIAMOND/SPADE/CLUB), and
         * picked up [suit] while not having it already
         */
        data class SuitPickedUp(
            override val playerId: Uuid,
            val spaceId: Uuid,
            val suit: SpaceType,
        ) : TurnEvent

        /**
         * [playerId] passed or landed on [spaceId], a BANK space, while holding all 4 suits
         * (HEART/DIAMOND/SPADE/CLUB) -- triggering a promotion: their held suits are cleared, their
         * promotion count goes up by one for next time, and they're paid [goldAwarded] gold (see
         * [checkPromotion] for the payout formula). Not emitted for a BANK space visited without
         * every suit already held.
         */
        data class Promoted(
            override val playerId: Uuid,
            val spaceId: Uuid,
            val goldAwarded: Int,
        ) : TurnEvent

        /**
         * Movement is paused on [spaceId] until [choosePath] is called with one of [options].
         * [movementPointsRemaining] is how many spaces the player still has left to move *after*
         * whichever option they pick -- e.g. a roll of 5 landing on a branch after 2 steps
         * leaves 3.
         */
        data class ChoiceRequired(
            override val playerId: Uuid,
            val spaceId: Uuid,
            val options: List<PathOption>,
            val movementPointsRemaining: Int,
        ) : TurnEvent

        /**
         * Movement ended on [spaceId], an unowned SHOP -- paused until [buyShop] or
         * [declineShopPurchase] decides what to do. Only ever emitted for a human; a computer
         * player decides immediately instead (see [ComputerPlayer.shouldBuyShop]).
         */
        data class ShopPurchaseAvailable(
            override val playerId: Uuid,
            val spaceId: Uuid,
            val price: Int,
        ) : TurnEvent

        /** [playerId] bought the shop at [spaceId] for [price], deducted from their gold. */
        data class ShopPurchased(
            override val playerId: Uuid,
            val spaceId: Uuid,
            val price: Int,
        ) : TurnEvent

        /**
         * [playerId] landed on [spaceId], a SHOP owned by [ownerId] (never [playerId] themselves --
         * see [payToll]), and paid them [amount] gold straight over -- see [tollAmount] for the
         * exact formula. Emitted the same way for a human or a computer player; neither is offered
         * a choice about it.
         */
        data class TollPaid(
            override val playerId: Uuid,
            val spaceId: Uuid,
            val ownerId: Uuid,
            val amount: Int,
        ) : TurnEvent

        /**
         * [playerId] passed or landed on [spaceId], a BANK space with at least one district's stock
         * to trade -- paused until [buyStock], [sellStock], or [skipStockTrade] decides what to do,
         * exactly one decision before movement resumes. [offers] names every district currently
         * tradeable, its price per share, and however much of it [playerId] already holds. Only
         * ever emitted for a human; a computer player decides immediately instead (see
         * [ComputerPlayer.chooseStockTrade]).
         */
        data class StockTradingAvailable(
            override val playerId: Uuid,
            val spaceId: Uuid,
            val offers: List<StockTradeOffer>,
        ) : TurnEvent

        /**
         * [playerId] bought [quantity] shares of [districtId]'s stock at [pricePerShare] each, for
         * [totalCost] gold, deducted from their gold.
         */
        data class StockPurchased(
            override val playerId: Uuid,
            val districtId: Uuid,
            val quantity: Int,
            val pricePerShare: Int,
            val totalCost: Int,
        ) : TurnEvent

        /**
         * [playerId] sold [quantity] shares of [districtId]'s stock at [pricePerShare] each, for
         * [totalProceeds] gold, credited to their gold.
         */
        data class StockSold(
            override val playerId: Uuid,
            val districtId: Uuid,
            val quantity: Int,
            val pricePerShare: Int,
            val totalProceeds: Int,
        ) : TurnEvent

        data class TurnEnded(
            override val playerId: Uuid,
            val turnNumber: Int,
            val gameOver: Boolean,
        ) : TurnEvent

        /**
         * It's now [playerId]'s turn, and nothing is going to happen on their behalf -- they need
         * to roll. Only ever emitted for a human; a computer player's turn gets played out
         * immediately instead (see [DiceRolled]), so there's nothing to announce ahead of it.
         */
        data class TurnStarted(
            override val playerId: Uuid,
            val turnNumber: Int,
        ) : TurnEvent
    }

    fun markReady(gameId: Uuid, playerId: Uuid): Result<ReadyOutcome> =
        withGameLock(gameId) {
            val game =
                gameDao.findById(gameId)
                    ?: return@withGameLock Result.failure(
                        GameNotFoundException("Game $gameId does not exist.")
                    )
            val players = playerDao.findByGameId(gameId)
            if (players.none { it.id.value == playerId }) {
                return@withGameLock Result.failure(
                    InvalidPlayerException("Player $playerId is not in game $gameId.")
                )
            }

            playerDao.updateStatus(playerId, PlayerStatus.READY)

            // Turn order is only ever decided once -- a player readying up again
            // after the game has already started (e.g. on reconnect) shouldn't
            // re-shuffle it.
            if (game.turnOrder != null) return@withGameLock Result.success(ReadyOutcome.Waiting)

            val (computerPlayers, humanPlayers) = players.partition { it.userId == null }
            val allHumansReady = humanPlayers.all { player ->
                val status =
                    if (player.id.value == playerId) PlayerStatus.READY
                    else playerDao.findState(player.id.value)?.status
                status == PlayerStatus.READY
            }
            if (!allHumansReady) return@withGameLock Result.success(ReadyOutcome.Waiting)

            // Nobody's connected to ready a computer player up themselves -- now
            // that every human is ready, do it for them so the game can start.
            computerPlayers.forEach { playerDao.updateStatus(it.id.value, PlayerStatus.READY) }

            val turnOrder = players.map { it.id.value }.shuffled()
            val startedGame = gameDao.startGame(gameId, turnOrder)

            // The game only ever starts once -- guarded by both the turnOrder check above and the
            // per-game lock this whole function runs under (the turnOrder check alone isn't
            // enough: two players readying up close enough together can both read turnOrder as
            // still null before either's start is visible to the other, and without the lock both
            // would go on to seed duplicate game_shop_information/game_district_information rows
            // -- this is the one moment a per-game copy of the board's shops (and each district's
            // stock) needs to be seeded -- see GameShopInformationDao.seedForGame and
            // GameDistrictInformationDao.seedForGame.
            if (startedGame != null) {
                boardDao.findById(game.boardId.value)?.let { boardGraph ->
                    val seededShops = gameShopInformationDao.seedForGame(gameId, boardGraph)
                    gameDistrictInformationDao.seedForGame(gameId, boardGraph, seededShops)
                }
            }

            // If the shuffle put one or more computer players at the front,
            // nobody's ever going to roll the dice to kick things off for them
            // -- play those turns out right now so the game doesn't stall before
            // a human even gets a chance to move.
            val playersById = players.associateBy { it.id.value }
            val openingTurnEvents =
                startedGame?.let { playComputerTurns(gameId, it, playersById) }.orEmpty()

            Result.success(ReadyOutcome.GameStarted(turnOrder, openingTurnEvents))
        }

    /**
     * A full snapshot of [gameId] as it stands right now, from [playerId]'s point of view (they
     * just need to be a real player in the game -- this isn't restricted to their own turn, unlike
     * every action method below). See GameSnapshot.kt for the shape, and GameWebSocketHandler,
     * which sends one of these to a client immediately after it connects so it doesn't have to have
     * watched every event live to know where things stand.
     */
    fun getSnapshot(gameId: Uuid, playerId: Uuid): Result<GameSnapshot> {
        val game =
            gameDao.findById(gameId)
                ?: return Result.failure(GameNotFoundException("Game $gameId does not exist."))
        val players = playerDao.findByGameId(gameId)
        if (players.none { it.id.value == playerId }) {
            return Result.failure(
                InvalidPlayerException("Player $playerId is not in game $gameId.")
            )
        }
        val boardGraph =
            boardDao.findById(game.boardId.value)
                ?: return Result.failure(
                    GameNotFoundException("Board for game $gameId no longer exists.")
                )

        val turnOrder = game.turnOrder
        val gameOver = isGameOver(game)
        val activePlayerId = turnOrder?.takeIf { !gameOver }?.let { it[game.turnNumber % it.size] }

        val playerSnapshots = players.map { player ->
            val state = playerDao.findState(player.id.value)
            val stockHoldings =
                playerStockDao.findByPlayer(player.id.value).mapNotNull { stock ->
                    val info =
                        gameDistrictInformationDao.findById(stock.gameDistrictInformationId.value)
                            ?: return@mapNotNull null
                    StockHoldingSnapshot(
                        districtId = info.districtId.value,
                        quantity = stock.quantity,
                    )
                }

            PlayerSnapshot(
                playerId = player.id.value,
                ready = state?.status == PlayerStatus.READY,
                currentSpaceId =
                    state?.currentSpaceId?.value
                        ?: turnOrder?.let { boardGraph.board.startSpaceId },
                currentGold = state?.currentGold ?: 0,
                heldSuits = state?.heldSuits.orEmpty(),
                promotionCount = state?.promotionCount ?: 0,
                ownedShopSpaceIds =
                    gameShopInformationDao.findOwnedByPlayer(gameId, player.id.value).map {
                        it.spaceId.value
                    },
                stockHoldings = stockHoldings,
            )
        }

        val pendingDecision = activePlayerId?.let {
            pendingDecisionFor(gameId, it, game, boardGraph)
        }

        return Result.success(
            GameSnapshot(
                turnOrder = turnOrder,
                turnNumber = game.turnNumber,
                gameOver = gameOver,
                activePlayerId = activePlayerId,
                pendingDecision = pendingDecision,
                players = playerSnapshots,
                shopValues =
                    gameShopInformationDao.findAllByGame(gameId).map {
                        ShopValueSnapshot(it.spaceId.value, it.currentValue)
                    },
                stockValues =
                    gameDistrictInformationDao.findAllByGame(gameId).map {
                        StockValueSnapshot(it.districtId.value, it.currentStockValue)
                    },
            )
        )
    }

    /**
     * Whichever decision (if any) [playerId] -- assumed to already be [game]'s activePlayerId --
     * currently has movement paused on, read back from persisted state rather than live from a move
     * actually happening: [Game.pendingStockTradeSpaceId] if set (see [checkStockTrade]), otherwise
     * [Game.currentMovementPoints] read the same way [pendingShopPurchase] (0, sitting on an
     * unowned shop) and [advanceMovement] (>0, sitting at a branch) already do. Null if none of
     * those match -- [playerId] just needs to roll.
     */
    private fun pendingDecisionFor(
        gameId: Uuid,
        playerId: Uuid,
        game: Game,
        boardGraph: BoardGraph,
    ): PendingDecisionSnapshot? {
        game.pendingStockTradeSpaceId?.let { spaceId ->
            return PendingDecisionSnapshot.StockTradePending(
                spaceId = spaceId.value,
                offers = stockOffers(gameId, playerId),
            )
        }

        val movementPoints = game.currentMovementPoints ?: return null
        val spaceId = playerDao.findState(playerId)?.currentSpaceId?.value ?: return null

        if (movementPoints == 0) {
            val shop =
                gameShopInformationDao.findByGameAndSpace(gameId, spaceId)?.takeIf {
                    it.ownerId == null
                } ?: return null
            return PendingDecisionSnapshot.ShopPurchasePending(spaceId, shop.currentValue)
        }

        val outgoing = boardGraph.paths.filter { it.fromSpaceId.value == spaceId }
        if (outgoing.size <= 1) return null
        return PendingDecisionSnapshot.ChoicePending(
            spaceId = spaceId,
            options =
                outgoing
                    .sortedBy { it.branchOrder }
                    .map { PathOption(it.toSpaceId.value, it.branchOrder) },
            movementPointsRemaining = movementPoints,
        )
    }

    /**
     * Rolls the die for [playerId]'s turn and moves them forward that many spaces -- automatically,
     * one at a time, until either movement runs out (ending the turn), a branch is reached and
     * paused on ([choosePath] picks up from there), a purchase decision is reached and paused on
     * ([buyShop]/[declineShopPurchase] picks up from there), a stock trade decision is reached and
     * paused on ([buyStock]/[sellStock]/ [skipStockTrade] picks up from there), or the game ends.
     * Whichever computer players immediately follow in turn order then get their own full turns
     * played out the same way, stopping once it's a human's turn again or the game ends. The result
     * is always at least one event (the roll) and is in order, so the caller can report each one
     * (e.g. as a broadcast per entry) as it happens.
     */
    fun rollDice(gameId: Uuid, playerId: Uuid): Result<List<TurnEvent>> =
        withGameLock(gameId) {
            val game =
                currentTurnGame(gameId, playerId).getOrElse {
                    return@withGameLock Result.failure(it)
                }
            if (game.currentMovementPoints != null) {
                return@withGameLock Result.failure(
                    InvalidTurnException(
                        "Player $playerId already rolled this turn -- choose a path to continue."
                    )
                )
            }

            val playersById = playerDao.findByGameId(gameId).associateBy { it.id.value }
            val player =
                playersById[playerId]
                    ?: return@withGameLock Result.failure(
                        InvalidPlayerException("Player $playerId is not in game $gameId.")
                    )
            val boardGraph =
                boardDao.findById(game.boardId.value)
                    ?: return@withGameLock Result.failure(
                        InvalidTurnException("Board for game $gameId no longer exists.")
                    )
            val state =
                playerDao.findState(playerId)
                    ?: return@withGameLock Result.failure(
                        InvalidPlayerException("Player $playerId has no state.")
                    )
            val fromSpaceId =
                state.currentSpaceId?.value
                    ?: boardGraph.board.startSpaceId
                    ?: return@withGameLock Result.failure(
                        InvalidTurnException("Board for game $gameId has no start space.")
                    )

            val roll = dice.roll()
            val events = mutableListOf<TurnEvent>(TurnEvent.DiceRolled(playerId, roll))
            val movement =
                advanceMovement(
                        gameId,
                        playerId,
                        player.userId == null,
                        game,
                        boardGraph,
                        fromSpaceId,
                        roll,
                    )
                    .getOrElse {
                        return@withGameLock Result.failure(it)
                    }
            events += movement.events
            events += chainComputerTurns(gameId, movement, playersById)

            return@withGameLock Result.success(events)
        }

    /**
     * Resumes [playerId]'s turn after it paused on a branch, moving them onto [toSpaceId] -- which
     * must be one of the options the pause offered -- and continuing movement (and any following
     * computer players' full turns) exactly as [rollDice] does.
     */
    fun choosePath(gameId: Uuid, playerId: Uuid, toSpaceId: Uuid): Result<List<TurnEvent>> =
        withGameLock(gameId) {
            val game =
                currentTurnGame(gameId, playerId).getOrElse {
                    return@withGameLock Result.failure(it)
                }
            val remaining =
                game.currentMovementPoints
                    ?: return@withGameLock Result.failure(
                        InvalidTurnException("Player $playerId hasn't rolled the dice yet.")
                    )
            if (game.pendingStockTradeSpaceId != null) {
                return@withGameLock Result.failure(
                    InvalidTurnException(
                        "Player $playerId has a stock trade decision pending -- resolve it first."
                    )
                )
            }

            val playersById = playerDao.findByGameId(gameId).associateBy { it.id.value }
            val player =
                playersById[playerId]
                    ?: return@withGameLock Result.failure(
                        InvalidPlayerException("Player $playerId is not in game $gameId.")
                    )
            val boardGraph =
                boardDao.findById(game.boardId.value)
                    ?: return@withGameLock Result.failure(
                        InvalidTurnException("Board for game $gameId no longer exists.")
                    )
            val state =
                playerDao.findState(playerId)
                    ?: return@withGameLock Result.failure(
                        InvalidPlayerException("Player $playerId has no state.")
                    )
            val currentSpaceId =
                state.currentSpaceId?.value
                    ?: boardGraph.board.startSpaceId
                    ?: return@withGameLock Result.failure(
                        InvalidTurnException("Board for game $gameId has no start space.")
                    )

            val outgoing = boardGraph.paths.filter { it.fromSpaceId.value == currentSpaceId }
            val chosenPath =
                outgoing.find { it.toSpaceId.value == toSpaceId }
                    ?: return@withGameLock Result.failure(
                        InvalidTurnException(
                            "$toSpaceId isn't a path out of player $playerId's current space."
                        )
                    )

            val isComputer = player.userId == null
            val movementPointsRemaining = remaining - 1
            val step =
                takeStep(
                    gameId,
                    playerId,
                    isComputer,
                    game.turnNumber,
                    currentSpaceId,
                    chosenPath,
                    movementPointsRemaining,
                    boardGraph,
                )
            val events = step.events.toMutableList()

            if (step.paused) {
                gameDao.setMovementPoints(gameId, movementPointsRemaining)
                return@withGameLock Result.success(events)
            }

            val movement =
                advanceMovement(
                        gameId,
                        playerId,
                        isComputer,
                        game,
                        boardGraph,
                        chosenPath.toSpaceId.value,
                        movementPointsRemaining,
                    )
                    .getOrElse {
                        return@withGameLock Result.failure(it)
                    }
            events += movement.events
            events += chainComputerTurns(gameId, movement, playersById)

            return@withGameLock Result.success(events)
        }

    /**
     * Buys the shop [playerId] is currently paused on (see [TurnEvent.ShopPurchaseAvailable]) for
     * its current price, deducted from their gold -- which can go negative afterward from other
     * causes not yet implemented, see PlayerStatesTable.currentGold, but a purchase itself always
     * requires enough gold on hand up front; fails if it doesn't. If this purchase brings the
     * player's owned count in that shop's district to 2 or more, every shop they own there
     * (including the one just bought) is recalculated per that district's progression (see
     * DistrictValueProgressionsTable). Ends the turn afterward and chains into any following
     * computer players' turns, exactly as [rollDice]/[choosePath] do.
     */
    fun buyShop(gameId: Uuid, playerId: Uuid): Result<List<TurnEvent>> =
        withGameLock(gameId) {
            val (game, shop, playersById) =
                pendingShopPurchase(gameId, playerId).getOrElse {
                    return@withGameLock Result.failure(it)
                }
            val gold =
                currentGold(playerId)
                    ?: return@withGameLock Result.failure(
                        InvalidPlayerException("Player $playerId has no state.")
                    )
            if (gold < shop.currentValue) {
                return@withGameLock Result.failure(
                    InvalidTurnException(
                        "Player $playerId can't afford this shop -- it costs ${shop.currentValue} but they only have $gold gold."
                    )
                )
            }

            val movement =
                endTurn(gameId, playerId, game, purchaseShop(gameId, playerId, shop)).getOrElse {
                    return@withGameLock Result.failure(it)
                }
            val events = movement.events + chainComputerTurns(gameId, movement, playersById)

            return@withGameLock Result.success(events)
        }

    /**
     * Declines the pending purchase from [TurnEvent.ShopPurchaseAvailable] and ends the turn
     * without buying.
     */
    fun declineShopPurchase(gameId: Uuid, playerId: Uuid): Result<List<TurnEvent>> =
        withGameLock(gameId) {
            val (game, _, playersById) =
                pendingShopPurchase(gameId, playerId).getOrElse {
                    return@withGameLock Result.failure(it)
                }

            val movement =
                endTurn(gameId, playerId, game, emptyList()).getOrElse {
                    return@withGameLock Result.failure(it)
                }
            val events = movement.events + chainComputerTurns(gameId, movement, playersById)

            return@withGameLock Result.success(events)
        }

    /**
     * Buys [quantity] shares (1-99, see [MIN_STOCK_TRADE_QUANTITY]/[MAX_STOCK_TRADE_QUANTITY]) of
     * [districtId]'s stock for [playerId], at that district's current price per share (see
     * [executeBuyStock]) -- resolving the pending decision from [TurnEvent.StockTradingAvailable]
     * and resuming movement from wherever it paused, exactly as [buyShop] resumes after a shop
     * purchase. Chains into any following computer players' turns exactly as
     * [rollDice]/[choosePath]/[buyShop] do.
     */
    fun buyStock(
        gameId: Uuid,
        playerId: Uuid,
        districtId: Uuid,
        quantity: Int,
    ): Result<List<TurnEvent>> =
        withGameLock(gameId) {
            val (game, playersById) =
                pendingStockTrade(gameId, playerId).getOrElse {
                    return@withGameLock Result.failure(it)
                }
            val purchased =
                executeBuyStock(gameId, playerId, districtId, quantity).getOrElse {
                    return@withGameLock Result.failure(it)
                }

            val movement =
                resumeAfterStockTrade(gameId, playerId, game, listOf(purchased)).getOrElse {
                    return@withGameLock Result.failure(it)
                }
            val events = movement.events + chainComputerTurns(gameId, movement, playersById)

            return@withGameLock Result.success(events)
        }

    /**
     * Sells [quantity] shares of [districtId]'s stock [playerId] currently holds (see
     * [executeSellStock]) -- otherwise exactly like [buyStock].
     */
    fun sellStock(
        gameId: Uuid,
        playerId: Uuid,
        districtId: Uuid,
        quantity: Int,
    ): Result<List<TurnEvent>> =
        withGameLock(gameId) {
            val (game, playersById) =
                pendingStockTrade(gameId, playerId).getOrElse {
                    return@withGameLock Result.failure(it)
                }
            val sold =
                executeSellStock(gameId, playerId, districtId, quantity).getOrElse {
                    return@withGameLock Result.failure(it)
                }

            val movement =
                resumeAfterStockTrade(gameId, playerId, game, listOf(sold)).getOrElse {
                    return@withGameLock Result.failure(it)
                }
            val events = movement.events + chainComputerTurns(gameId, movement, playersById)

            return@withGameLock Result.success(events)
        }

    /**
     * Declines the pending trade from [TurnEvent.StockTradingAvailable] and resumes movement
     * without buying or selling anything.
     */
    fun skipStockTrade(gameId: Uuid, playerId: Uuid): Result<List<TurnEvent>> =
        withGameLock(gameId) {
            val (game, playersById) =
                pendingStockTrade(gameId, playerId).getOrElse {
                    return@withGameLock Result.failure(it)
                }

            val movement =
                resumeAfterStockTrade(gameId, playerId, game, emptyList()).getOrElse {
                    return@withGameLock Result.failure(it)
                }
            val events = movement.events + chainComputerTurns(gameId, movement, playersById)

            return@withGameLock Result.success(events)
        }

    /**
     * Validates that it's actually [playerId]'s turn to act right now, returning the game if so.
     */
    private fun currentTurnGame(gameId: Uuid, playerId: Uuid): Result<Game> {
        val game =
            gameDao.findById(gameId)
                ?: return Result.failure(GameNotFoundException("Game $gameId does not exist."))
        val turnOrder =
            game.turnOrder
                ?: return Result.failure(
                    InvalidTurnException(
                        "Game $gameId hasn't started yet -- not everyone is ready."
                    )
                )
        if (isGameOver(game)) {
            return Result.failure(InvalidTurnException("Game $gameId is already over."))
        }

        val currentPlayerId = turnOrder[game.turnNumber % turnOrder.size]
        if (currentPlayerId != playerId) {
            return Result.failure(InvalidTurnException("It isn't player $playerId's turn."))
        }

        return Result.success(game)
    }

    /**
     * Validates that [playerId] actually has a shop purchase decision pending -- i.e. movement
     * ended this turn on an unowned shop (see [TurnEvent.ShopPurchaseAvailable]) -- for
     * [buyShop]/[declineShopPurchase]. currentMovementPoints is reused as the pause signal here
     * exactly like it is for a branch choice: 0 specifically (rather than null, or >0 for a branch)
     * means movement finished but the turn hasn't ended yet, waiting on this decision.
     */
    private fun pendingShopPurchase(
        gameId: Uuid,
        playerId: Uuid,
    ): Result<Triple<Game, GameShopInformation, Map<Uuid, Player>>> {
        val game =
            currentTurnGame(gameId, playerId).getOrElse {
                return Result.failure(it)
            }
        if (game.currentMovementPoints != 0) {
            return Result.failure(
                InvalidTurnException("Player $playerId has no shop purchase decision pending.")
            )
        }

        val state =
            playerDao.findState(playerId)
                ?: return Result.failure(InvalidPlayerException("Player $playerId has no state."))
        val spaceId =
            state.currentSpaceId?.value
                ?: return Result.failure(
                    InvalidTurnException("Player $playerId has no current space.")
                )
        val shop =
            gameShopInformationDao.findByGameAndSpace(gameId, spaceId)?.takeIf {
                it.ownerId == null
            }
                ?: return Result.failure(
                    InvalidTurnException("There's no shop purchase pending for player $playerId.")
                )

        val playersById = playerDao.findByGameId(gameId).associateBy { it.id.value }
        return Result.success(Triple(game, shop, playersById))
    }

    /**
     * Validates that [playerId] actually has a stock trade decision pending -- i.e. movement paused
     * this turn on a BANK space with stock to trade (see [TurnEvent.StockTradingAvailable]) -- for
     * [buyStock]/[sellStock]/[skipStockTrade]. Unlike [pendingShopPurchase], this doesn't reuse
     * currentMovementPoints as the signal (this pause can happen with movement still left) -- see
     * GamesTable.pendingStockTradeSpaceId.
     */
    private fun pendingStockTrade(
        gameId: Uuid,
        playerId: Uuid,
    ): Result<Pair<Game, Map<Uuid, Player>>> {
        val game =
            currentTurnGame(gameId, playerId).getOrElse {
                return Result.failure(it)
            }
        if (game.pendingStockTradeSpaceId == null) {
            return Result.failure(
                InvalidTurnException("Player $playerId has no stock trade decision pending.")
            )
        }

        val playersById = playerDao.findByGameId(gameId).associateBy { it.id.value }
        return Result.success(game to playersById)
    }

    private data class MovementResult(val events: List<TurnEvent>, val updatedGame: Game)

    /**
     * Moves a player forward from [startingSpaceId] with [startingMovementPoints] left to spend --
     * one space at a time -- stopping in one of five ways: movement runs out on an ordinary space
     * (the turn ends), it runs out on an unowned shop (a human pauses for a purchase decision; a
     * computer decides immediately and the turn ends), any space along the way is a BANK space with
     * stock to trade (a human pauses for a trade decision; a computer decides immediately and
     * movement keeps going -- see [checkStockTrade]), a computer player is moving and hits a branch
     * (picks randomly and keeps going, so pausing never actually happens for one), or a human
     * player hits a branch (pauses here, persisting the remaining movement so a later [choosePath]
     * call, even after a reconnect, can pick up from it).
     */
    private fun advanceMovement(
        gameId: Uuid,
        playerId: Uuid,
        isComputer: Boolean,
        game: Game,
        boardGraph: BoardGraph,
        startingSpaceId: Uuid,
        startingMovementPoints: Int,
    ): Result<MovementResult> {
        val events = mutableListOf<TurnEvent>()
        var currentSpaceId = startingSpaceId
        var remaining = startingMovementPoints

        while (remaining > 0) {
            val outgoing = boardGraph.paths.filter { it.fromSpaceId.value == currentSpaceId }
            if (outgoing.isEmpty()) {
                return Result.failure(
                    InvalidTurnException("No path forward from player $playerId's current space.")
                )
            }

            if (outgoing.size > 1 && !isComputer) {
                gameDao.setMovementPoints(gameId, remaining)
                events +=
                    TurnEvent.ChoiceRequired(
                        playerId = playerId,
                        spaceId = currentSpaceId,
                        options =
                            outgoing
                                .sortedBy { it.branchOrder }
                                .map { PathOption(it.toSpaceId.value, it.branchOrder) },
                        movementPointsRemaining = remaining,
                    )
                return Result.success(MovementResult(events, game))
            }

            val chosenPath =
                if (outgoing.size > 1) computerPlayer.chooseBranch(outgoing) else outgoing.single()
            remaining -= 1
            val step =
                takeStep(
                    gameId,
                    playerId,
                    isComputer,
                    game.turnNumber,
                    currentSpaceId,
                    chosenPath,
                    remaining,
                    boardGraph,
                )
            events += step.events
            currentSpaceId = chosenPath.toSpaceId.value

            if (step.paused) {
                gameDao.setMovementPoints(gameId, remaining)
                return Result.success(MovementResult(events, game))
            }
        }

        val shopHere = gameShopInformationDao.findByGameAndSpace(gameId, currentSpaceId)
        when {
            shopHere != null && shopHere.ownerId == null -> {
                if (!isComputer) {
                    gameDao.setMovementPoints(gameId, 0)
                    events +=
                        TurnEvent.ShopPurchaseAvailable(
                            playerId,
                            currentSpaceId,
                            shopHere.currentValue,
                        )
                    return Result.success(MovementResult(events, game))
                }

                // ComputerPlayer decides whether it *wants* the shop, given its real currentGold to
                // weigh -- but affordability itself is still enforced here, same floor a human's
                // buyShop gets, regardless of what shouldBuyShop says (see its doc).
                if (
                    computerPlayer.shouldBuyShop(shopHere, currentGold(playerId) ?: 0) &&
                        canAfford(playerId, shopHere.currentValue)
                ) {
                    events += purchaseShop(gameId, playerId, shopHere)
                }
            }
            // ownerId is never null here -- the branch above already claimed that case -- but a
            // safe call still reads better than a non-null assertion this far from the check.
            shopHere != null && shopHere.ownerId?.value != playerId -> {
                events += payToll(playerId, shopHere)
            }
        }

        return endTurn(gameId, playerId, game, events)
    }

    /**
     * [playerId]'s net worth in [gameId] -- their current gold on hand, plus every shop they own
     * (see GameShopInformationDao.findOwnedByPlayer), plus the current value of every district's
     * stock they hold (their held quantity times that district's current_stock_value -- see
     * GameDistrictInformationDao). Used by [endGameIfNetWorthReached] to check the target-net-worth
     * ending condition.
     */
    private fun netWorth(gameId: Uuid, playerId: Uuid): Int {
        val gold = playerDao.findState(playerId)?.currentGold ?: 0
        val shopValue =
            gameShopInformationDao.findOwnedByPlayer(gameId, playerId).sumOf { it.currentValue }
        val stockValue =
            playerStockDao.findByPlayer(playerId).sumOf { stock ->
                val info =
                    gameDistrictInformationDao.findById(stock.gameDistrictInformationId.value)
                (info?.currentStockValue ?: 0) * stock.quantity
            }
        return gold + shopValue + stockValue
    }

    /**
     * A game is over once either turnNumber has reached maxTurns, or it was ended early because
     * some player's net worth reached targetNetWorth (see [endGameIfNetWorthReached]) -- recorded
     * as [Game.endedOnTurn], which stays null the whole time a game is still in progress.
     */
    private fun isGameOver(game: Game): Boolean =
        game.endedOnTurn != null || game.turnNumber >= game.maxTurns

    /**
     * Ends [gameId] early -- deciding whether and when to do so is this service's job, not
     * GameDao's (see [GameDao.setEndedOnTurn]) -- the moment any of its players' net worth (see
     * [netWorth]) reaches or exceeds [game]'s targetNetWorth. Only bothers checking when
     * [precedingEvents] shows something that could actually have moved a net worth this turn -- a
     * shop purchase, a district value progression, a stock trade, a promotion payout, or a toll
     * payment (gold on hand counts now -- see [netWorth]) -- so a turn that's just movement or a
     * suit pickup never touches
     * PlayerDao/GameShopInformationDao/PlayerStockDao/GameDistrictInformationDao at all.
     * Checked across every player, not just the one who acted, since a stock trade's price
     * fluctuation (see [fluctuateStockPrice]) can move the value of shares a *different* player
     * holds. A no-op if nobody has crossed it, or if [game] was already ended -- never overwrites
     * an already-recorded endedOnTurn.
     */
    private fun endGameIfNetWorthReached(
        gameId: Uuid,
        game: Game,
        precedingEvents: List<TurnEvent>,
    ) {
        val netWorthMayHaveMoved = precedingEvents.any {
            it is TurnEvent.ShopPurchased ||
                it is TurnEvent.StockPurchased ||
                it is TurnEvent.StockSold ||
                it is TurnEvent.Promoted ||
                it is TurnEvent.TollPaid
        }
        if (!netWorthMayHaveMoved) return
        if (game.endedOnTurn != null) return

        val playerIds = playerDao.findByGameId(gameId).map { it.id.value }
        if (playerIds.any { netWorth(gameId, it) >= game.targetNetWorth }) {
            gameDao.setEndedOnTurn(gameId, game.turnNumber)
        }
    }

    /**
     * Advances turnNumber and appends [TurnEvent.TurnEnded] to [precedingEvents] -- the shared tail
     * of every way a turn can finish.
     */
    private fun endTurn(
        gameId: Uuid,
        playerId: Uuid,
        game: Game,
        precedingEvents: List<TurnEvent>,
    ): Result<MovementResult> {
        endGameIfNetWorthReached(gameId, game, precedingEvents)
        val updatedGame =
            gameDao.advanceTurn(gameId)
                ?: return Result.failure(GameNotFoundException("Game $gameId does not exist."))
        val events =
            precedingEvents +
                TurnEvent.TurnEnded(
                    playerId,
                    game.turnNumber,
                    gameOver = isGameOver(updatedGame),
                )
        return Result.success(MovementResult(events, updatedGame))
    }

    /**
     * Pays [shop]'s current price out of [playerId]'s gold and hands them ownership, then -- if
     * [shop] is in a district -- recalculates max_cap for every shop [playerId] now owns there
     * (including the one just bought), since their dominance level in that district just changed
     * (see [recalculateMaxCaps]). Unlike toll price, a shop's currentValue is never touched by a
     * purchase -- it only ever grows through direct investment (not yet implemented) -- so there's
     * nothing here for current_stock_value (derived purely from currentValue, see
     * GameDistrictInformationDao) to recompute either.
     *
     * Every caller reaches this only after confirming [shop] is unowned (see
     * [pendingShopPurchase]'s own `ownerId == null` filter), and every caller runs under
     * GameSimulationService.gameLocks besides -- so [GameShopInformationDao.setOwner] declining to
     * hand over an already-owned shop is not a path that's actually reachable here. Its return
     * value is deliberately not checked: a non-null there would mean the lock itself has a hole,
     * which is a bug to find and fix, not a condition for this method to degrade gracefully around.
     */
    private fun purchaseShop(
        gameId: Uuid,
        playerId: Uuid,
        shop: GameShopInformation,
    ): List<TurnEvent> {
        val price = shop.currentValue
        playerDao.adjustGold(playerId, -price)
        gameShopInformationDao.setOwner(shop.id.value, playerId)

        val districtId = shop.districtId
        if (districtId != null) {
            recalculateMaxCaps(gameId, playerId, districtId)
        }

        return listOf(TurnEvent.ShopPurchased(playerId, shop.spaceId.value, price))
    }

    /**
     * Recomputes and persists max_cap for every shop [playerId] owns in [districtId] -- called
     * right after a purchase changes how many of them that is, since max_cap's ceiling depends on
     * that count (see DistrictValueProgressionsTable.maxCapitalMultiplier). Each shop's ceiling is
     * its own baseValue times [maxCapitalMultiplierFor] that owned-shop-count, and its new max_cap
     * is that ceiling minus its currentValue -- floored at 0, the same way an already-maxed-out
     * shop can never go negative.
     */
    private fun recalculateMaxCaps(gameId: Uuid, playerId: Uuid, districtId: EntityID<Uuid>) {
        val owned = gameShopInformationDao.findOwnedByPlayerInDistrict(gameId, playerId, districtId)
        val multiplier = maxCapitalMultiplierFor(districtId, owned.size)

        owned.forEach { shop ->
            val ceiling =
                (BigDecimal(shop.baseValue) * multiplier).setScale(0, RoundingMode.HALF_UP).toInt()
            gameShopInformationDao.setMaxCap(shop.id.value, maxOf(ceiling - shop.currentValue, 0))
        }
    }

    /**
     * How many shops [ownerId] currently owns in [districtId] -- the "dominance level" that
     * [priceMultiplierFor]/[maxCapitalMultiplierFor] key off of. 0 (the unowned/no-district
     * baseline) if either is null, without ever querying
     * [GameShopInformationDao.findOwnedByPlayerInDistrict] for a case that couldn't have a
     * district row anyway.
     */
    private fun ownedShopCountFor(gameId: Uuid, ownerId: Uuid?, districtId: EntityID<Uuid>?): Int {
        if (ownerId == null || districtId == null) return 0
        return gameShopInformationDao.findOwnedByPlayerInDistrict(gameId, ownerId, districtId).size
    }

    /**
     * The toll price multiplier for a shop whose owner currently owns [ownedShopCount] shops in
     * [districtId] -- looked up fresh from DistrictValueProgressionsTable every time, never
     * compounded or cached. 1 (the implied baseline) if there's no district, fewer than 2 owned
     * shops, or no progression row defined for that exact count.
     */
    private fun priceMultiplierFor(districtId: EntityID<Uuid>?, ownedShopCount: Int): BigDecimal {
        if (districtId == null || ownedShopCount < MIN_SHOPS_OWNED_TO_RECALCULATE) {
            return BigDecimal.ONE
        }
        return boardDao.findDistrictValueProgression(districtId, ownedShopCount)?.priceMultiplier
            ?: BigDecimal.ONE
    }

    /** Same idea as [priceMultiplierFor], but for max_cap's ceiling multiplier instead. */
    private fun maxCapitalMultiplierFor(districtId: EntityID<Uuid>?, ownedShopCount: Int): BigDecimal {
        if (districtId == null || ownedShopCount < MIN_SHOPS_OWNED_TO_RECALCULATE) {
            return BigDecimal.ONE
        }
        return boardDao.findDistrictValueProgression(districtId, ownedShopCount)
            ?.maxCapitalMultiplier ?: BigDecimal.ONE
    }

    /**
     * The toll [shop] charges anyone who lands on it besides its owner. Weights currentValue
     * above baseValue twice as heavily as the base amount itself (currentValue*2 - baseValue,
     * which is just baseValue when currentValue == baseValue, i.e. before any investment), scales
     * that by basePricePercentage the same way a flat toll always has, and then by
     * [priceMultiplierFor] for however many shops in [shop]'s district its owner currently owns --
     * so buying more shops in a district raises toll immediately, without ever touching any shop's
     * stored currentValue. Rounded to the nearest gold, floored at 1 so a shop whose currentValue
     * has fallen well below baseValue (not currently possible, but not assumed away either) never
     * charges a non-positive toll.
     */
    private fun tollAmount(shop: GameShopInformation): Int {
        val ownedShopCount = ownedShopCountFor(shop.gameId.value, shop.ownerId?.value, shop.districtId)
        val multiplier = priceMultiplierFor(shop.districtId, ownedShopCount)
        val weightedValue = BigDecimal(shop.currentValue.toLong() * 2 - shop.baseValue)
        val amount =
            (weightedValue * shop.basePricePercentage * multiplier)
                .setScale(0, RoundingMode.HALF_UP)
                .toInt()
        return maxOf(amount, 1)
    }

    /**
     * Pays [tollAmount] of [shop] straight from [playerId] to [shop]'s owner -- called only once
     * [advanceMovement] has confirmed [shop] is both owned and not owned by [playerId] themselves,
     * so [shop].ownerId is trusted non-null here. Unlike [purchaseShop], there's no affordability
     * floor: a toll is owed regardless of whether [playerId] can cover it, so this can take their
     * gold negative, same as a shop purchase already can from other causes.
     */
    private fun payToll(playerId: Uuid, shop: GameShopInformation): List<TurnEvent> {
        val ownerId =
            checkNotNull(shop.ownerId) { "Shop ${shop.id.value} has no owner to pay toll to." }
                .value
        val amount = tollAmount(shop)

        playerDao.adjustGold(playerId, -amount)
        playerDao.adjustGold(ownerId, amount)

        return listOf(TurnEvent.TollPaid(playerId, shop.spaceId.value, ownerId, amount))
    }

    /**
     * Buys [quantity] shares (1-99) of [districtId]'s stock for [playerId], at that district's
     * current_stock_value per share -- paid out of their gold, which must cover the full quantity *
     * price up front; fails outright otherwise, exactly like [buyShop]. A quantity over
     * [STOCK_FLUCTUATION_THRESHOLD] also nudges the district's price up afterward -- see
     * [fluctuateStockPrice]. Shared by the human-facing [buyStock] and a computer player's
     * automatic decision in [checkStockTrade].
     */
    private fun executeBuyStock(
        gameId: Uuid,
        playerId: Uuid,
        districtId: Uuid,
        quantity: Int,
    ): Result<TurnEvent.StockPurchased> {
        if (quantity !in MIN_STOCK_TRADE_QUANTITY..MAX_STOCK_TRADE_QUANTITY) {
            return Result.failure(
                InvalidTurnException(
                    "Quantity must be between $MIN_STOCK_TRADE_QUANTITY and $MAX_STOCK_TRADE_QUANTITY shares."
                )
            )
        }
        val info =
            gameDistrictInformationDao.findByGameAndDistrict(gameId, districtId)
                ?: return Result.failure(
                    InvalidTurnException(
                        "District $districtId has no stock to trade in game $gameId."
                    )
                )

        val pricePerShare = info.currentStockValue
        val totalCost = pricePerShare * quantity
        val gold =
            currentGold(playerId)
                ?: return Result.failure(InvalidPlayerException("Player $playerId has no state."))
        if (gold < totalCost) {
            return Result.failure(
                InvalidTurnException(
                    "Player $playerId can't afford $quantity shares of district $districtId -- it costs $totalCost but they only have $gold gold."
                )
            )
        }

        playerDao.adjustGold(playerId, -totalCost)
        playerStockDao.adjustQuantity(playerId, info.id.value, quantity)
        if (quantity > STOCK_FLUCTUATION_THRESHOLD) {
            fluctuateStockPrice(gameId, info, isBuy = true)
        }
        return Result.success(
            TurnEvent.StockPurchased(playerId, districtId, quantity, pricePerShare, totalCost)
        )
    }

    /**
     * Sells [quantity] shares (1-99) of [districtId]'s stock [playerId] currently holds, at that
     * district's current_stock_value per share -- credited to their gold. Can't sell more than
     * currently held; fails outright otherwise. A quantity over [STOCK_FLUCTUATION_THRESHOLD] also
     * nudges the district's price down afterward -- see [fluctuateStockPrice]. Shared by the
     * human-facing [sellStock] and a computer player's automatic decision in [checkStockTrade].
     */
    private fun executeSellStock(
        gameId: Uuid,
        playerId: Uuid,
        districtId: Uuid,
        quantity: Int,
    ): Result<TurnEvent.StockSold> {
        if (quantity !in MIN_STOCK_TRADE_QUANTITY..MAX_STOCK_TRADE_QUANTITY) {
            return Result.failure(
                InvalidTurnException(
                    "Quantity must be between $MIN_STOCK_TRADE_QUANTITY and $MAX_STOCK_TRADE_QUANTITY shares."
                )
            )
        }
        val info =
            gameDistrictInformationDao.findByGameAndDistrict(gameId, districtId)
                ?: return Result.failure(
                    InvalidTurnException(
                        "District $districtId has no stock to trade in game $gameId."
                    )
                )

        val owned = playerStockDao.find(playerId, info.id.value)?.quantity ?: 0
        if (quantity > owned) {
            return Result.failure(
                InvalidTurnException(
                    "Player $playerId only owns $owned shares of district $districtId -- can't sell $quantity."
                )
            )
        }

        val pricePerShare = info.currentStockValue
        val totalProceeds = pricePerShare * quantity
        playerDao.adjustGold(playerId, totalProceeds)
        playerStockDao.adjustQuantity(playerId, info.id.value, -quantity)
        if (quantity > STOCK_FLUCTUATION_THRESHOLD) {
            fluctuateStockPrice(gameId, info, isBuy = false)
        }
        return Result.success(
            TurnEvent.StockSold(playerId, districtId, quantity, pricePerShare, totalProceeds)
        )
    }

    /**
     * Works out and persists [info]'s post-trade stock price, for a trade of more than
     * [STOCK_FLUCTUATION_THRESHOLD] shares at once -- [executeBuyStock]/[executeSellStock] are the
     * only callers, and only once that threshold is crossed. Moves [info]'s currentStockValue by
     * its own pre-trade value divided by [STOCK_FLUCTUATION_DIVISOR] (rounded down), plus 1 -- up
     * for a buy ([isBuy] true), down for a sell -- but never lets a sell push it below the
     * district's minimum: current_stock_value recomputed fresh from [info]'s district's current
     * shops via [averageStockValue] (the same formula
     * GameDistrictInformationDao.recalculateCurrentStockValue uses), since shop values -- and so
     * this floor -- can rise over the course of a game as shops are invested in. The
     * decision lives here rather than on GameDistrictInformationDao, which just persists whatever
     * price this settles on (see GameDistrictInformationDao.setCurrentStockValue).
     */
    private fun fluctuateStockPrice(gameId: Uuid, info: GameDistrictInformation, isBuy: Boolean) {
        val shops = gameShopInformationDao.findByGameAndDistrict(gameId, info.districtId)
        val delta = info.currentStockValue / STOCK_FLUCTUATION_DIVISOR + 1
        val fluctuated =
            if (isBuy) info.currentStockValue + delta else info.currentStockValue - delta
        val minimum =
            if (shops.isEmpty()) info.currentStockValue
            else averageStockValue(shops, info.minimumStockPercentage)

        gameDistrictInformationDao.setCurrentStockValue(info.id.value, maxOf(fluctuated, minimum))
    }

    /**
     * The average currentValue of [shops], multiplied by [minimumStockPercentage] and rounded to
     * the nearest whole gold -- a district's current_stock_value floor (see [fluctuateStockPrice]).
     * Mirrors GameDistrictInformationDao's own private seeding/recalculation formula; kept as a
     * separate copy here rather than shared so that DAO stays limited to querying and persisting,
     * not deciding prices.
     */
    private fun averageStockValue(
        shops: List<GameShopInformation>,
        minimumStockPercentage: BigDecimal,
    ): Int {
        val average =
            shops
                .sumOf { it.currentValue }
                .toBigDecimal()
                .divide(
                    shops.size.toBigDecimal(),
                    STOCK_AVERAGE_INTERMEDIATE_SCALE,
                    RoundingMode.HALF_UP,
                )
        return (average * minimumStockPercentage).setScale(0, RoundingMode.HALF_UP).toInt()
    }

    /**
     * Clears the pending stock trade decision and continues wherever movement paused for it --
     * ending the turn if no movement was left, or resuming movement from the player's current
     * position with whatever was left otherwise. [precedingEvents] (a StockPurchased, StockSold, or
     * nothing for a skip) leads whatever comes next.
     */
    private fun resumeAfterStockTrade(
        gameId: Uuid,
        playerId: Uuid,
        game: Game,
        precedingEvents: List<TurnEvent>,
    ): Result<MovementResult> {
        gameDao.setPendingStockTradeSpace(gameId, null)

        val remaining = game.currentMovementPoints ?: 0
        if (remaining <= 0) {
            return endTurn(gameId, playerId, game, precedingEvents)
        }

        val boardGraph =
            boardDao.findById(game.boardId.value)
                ?: return Result.failure(
                    InvalidTurnException("Board for game $gameId no longer exists.")
                )
        val state =
            playerDao.findState(playerId)
                ?: return Result.failure(InvalidPlayerException("Player $playerId has no state."))
        val currentSpaceId =
            state.currentSpaceId?.value
                ?: return Result.failure(
                    InvalidTurnException("Player $playerId has no current space.")
                )

        // Only a human ever gets here -- buyStock/sellStock/skipStockTrade are all responses to
        // a pause that, per [checkStockTrade], only ever happens for a human in the first place.
        val movement =
            advanceMovement(
                    gameId,
                    playerId,
                    isComputer = false,
                    game,
                    boardGraph,
                    currentSpaceId,
                    remaining,
                )
                .getOrElse {
                    return Result.failure(it)
                }
        return Result.success(
            MovementResult(precedingEvents + movement.events, movement.updatedGame)
        )
    }

    /** Null only if [playerId] somehow has no state at all -- see [PlayerDao.findState]. */
    private fun currentGold(playerId: Uuid): Int? = playerDao.findState(playerId)?.currentGold

    /**
     * Whether [playerId] currently has at least [price] gold on hand -- a missing state counts as
     * no.
     */
    private fun canAfford(playerId: Uuid, price: Int): Boolean =
        (currentGold(playerId) ?: 0) >= price

    private data class StepResult(val events: List<TurnEvent>, val paused: Boolean)

    /**
     * Moves [playerId] onto [path]'s destination and returns the resulting events -- always a
     * leading [TurnEvent.Moved], plus a [TurnEvent.SuitPickedUp] if that destination is a suit
     * space and picking it up was actually new (see [pickUpSuit]), plus a [TurnEvent.Promoted] if
     * it's a BANK space reached while holding all 4 suits (see [checkPromotion]), plus whatever
     * [checkStockTrade] adds for that same BANK space, independent of whether a promotion actually
     * happened. [StepResult.paused] is true if that last check paused movement for a human's stock
     * trade decision -- callers must stop advancing movement themselves when it is, exactly as they
     * already do for a branch pause. Every space a player is moved onto over the course of a turn
     * -- passed through mid-move or landed on at the end -- flows through here exactly once.
     */
    private fun takeStep(
        gameId: Uuid,
        playerId: Uuid,
        isComputer: Boolean,
        turnNumber: Int,
        fromSpaceId: Uuid,
        path: BoardPath,
        movementPointsRemaining: Int,
        boardGraph: BoardGraph,
    ): StepResult {
        playerDao.updatePosition(playerId, path.toSpaceId.value)
        val moved =
            TurnEvent.Moved(
                playerId,
                turnNumber,
                fromSpaceId,
                path.toSpaceId.value,
                movementPointsRemaining,
            )
        val events = mutableListOf<TurnEvent>(moved)
        events += pickUpSuit(playerId, path.toSpaceId.value, boardGraph)
        events += checkPromotion(gameId, playerId, path.toSpaceId.value, boardGraph)

        val trade = checkStockTrade(gameId, playerId, isComputer, path.toSpaceId.value, boardGraph)
        events += trade.events

        return StepResult(events, trade.paused)
    }

    /**
     * [spaceId] is picked up as a suit for [playerId] if it's a HEART/DIAMOND/SPADE/CLUB space (see
     * [SUIT_SPACE_TYPES]) -- a no-op, emitting nothing, for any other space type or one [playerId]
     * already holds (see [PlayerDao.addHeldSuit]).
     */
    private fun pickUpSuit(playerId: Uuid, spaceId: Uuid, boardGraph: BoardGraph): List<TurnEvent> {
        val suit =
            boardGraph.spaces
                .find { it.id.value == spaceId }
                ?.spaceType
                ?.takeIf { it in SUIT_SPACE_TYPES } ?: return emptyList()
        val pickedUp = playerDao.addHeldSuit(playerId, suit) ?: return emptyList()

        return if (pickedUp) listOf(TurnEvent.SuitPickedUp(playerId, spaceId, suit))
        else emptyList()
    }

    /**
     * [spaceId] triggers a promotion for [playerId] if it's a BANK space (see [SpaceType]) and they
     * currently hold all 4 suits. The payout is board.baseSalary + (board.promotionBonus * their
     * promotionCount so far, i.e. how many times they've already been promoted -- see
     * PlayerStatesTable) + the current value of every shop they own in [gameId] (see
     * GameShopInformationDao.findOwnedByPlayer), paid via [PlayerDao.adjustGold]. Their held suits
     * are then cleared (see [PlayerDao.clearHeldSuits]) and their promotion count bumped for next
     * time (see [PlayerDao.incrementPromotionCount]). A no-op, emitting nothing, for any other
     * space type or a player who doesn't hold every suit yet.
     */
    private fun checkPromotion(
        gameId: Uuid,
        playerId: Uuid,
        spaceId: Uuid,
        boardGraph: BoardGraph,
    ): List<TurnEvent> {
        boardGraph.spaces
            .find { it.id.value == spaceId }
            ?.spaceType
            ?.takeIf { it == SpaceType.BANK } ?: return emptyList()
        val state = playerDao.findState(playerId) ?: return emptyList()
        if (!SUIT_SPACE_TYPES.all { it.name in state.heldSuits }) return emptyList()

        val ownedShopValue =
            gameShopInformationDao.findOwnedByPlayer(gameId, playerId).sumOf { it.currentValue }
        val goldAwarded =
            boardGraph.board.baseSalary +
                (boardGraph.board.promotionBonus * state.promotionCount) +
                ownedShopValue

        playerDao.clearHeldSuits(playerId)
        playerDao.incrementPromotionCount(playerId)
        playerDao.adjustGold(playerId, goldAwarded)

        return listOf(TurnEvent.Promoted(playerId, spaceId, goldAwarded))
    }

    private data class StockTradeCheck(val events: List<TurnEvent>, val paused: Boolean)

    /**
     * [spaceId] offers a stock trade decision for [playerId] if it's a BANK space (see [SpaceType])
     * with at least one district's stock available to trade -- checked on every space passed or
     * landed on, exactly alongside [checkPromotion], independent of whether a promotion actually
     * happened there. A human player pauses here (a [TurnEvent.StockTradingAvailable] naming every
     * tradeable district -- see [stockOffers]) until [buyStock]/[sellStock]/[skipStockTrade]
     * resolves it, persisted via games.pending_stock_trade_space_id (see GamesTable) since --
     * unlike a shop purchase -- this can happen with movement still left, so
     * current_movement_points alone can't double as the pause signal here. A computer player
     * instead decides immediately via [ComputerPlayer.chooseStockTrade] and never pauses -- see
     * [executeBuyStock]/ [executeSellStock] for the same affordability/ownership floors enforced
     * either way. A no-op for any other space type, or a BANK space with no district stock to
     * trade.
     */
    private fun checkStockTrade(
        gameId: Uuid,
        playerId: Uuid,
        isComputer: Boolean,
        spaceId: Uuid,
        boardGraph: BoardGraph,
    ): StockTradeCheck {
        boardGraph.spaces
            .find { it.id.value == spaceId }
            ?.spaceType
            ?.takeIf { it == SpaceType.BANK } ?: return StockTradeCheck(emptyList(), paused = false)

        val offers = stockOffers(gameId, playerId)
        if (offers.isEmpty()) return StockTradeCheck(emptyList(), paused = false)

        if (!isComputer) {
            gameDao.setPendingStockTradeSpace(gameId, spaceId)
            return StockTradeCheck(
                listOf(TurnEvent.StockTradingAvailable(playerId, spaceId, offers)),
                paused = true,
            )
        }

        val event =
            when (
                val decision = computerPlayer.chooseStockTrade(offers, currentGold(playerId) ?: 0)
            ) {
                is StockTradeDecision.Buy ->
                    executeBuyStock(gameId, playerId, decision.districtId, decision.quantity)
                        .getOrNull()
                is StockTradeDecision.Sell ->
                    executeSellStock(gameId, playerId, decision.districtId, decision.quantity)
                        .getOrNull()
                null -> null
            }
        return StockTradeCheck(listOfNotNull(event), paused = false)
    }

    /**
     * Every district in [gameId] with stock to trade, from [playerId]'s point of view (their
     * current holding in each, if any).
     */
    private fun stockOffers(gameId: Uuid, playerId: Uuid): List<StockTradeOffer> {
        val ownedByInfoId =
            playerStockDao.findByPlayer(playerId).associate {
                it.gameDistrictInformationId.value to it.quantity
            }
        return gameDistrictInformationDao.findAllByGame(gameId).map { info ->
            StockTradeOffer(
                districtId = info.districtId.value,
                gameDistrictInformationId = info.id.value,
                pricePerShare = info.currentStockValue,
                ownedQuantity = ownedByInfoId[info.id.value] ?: 0,
            )
        }
    }

    /**
     * Only chains into the next player(s) once [movement] actually ended the turn rather than
     * pausing on a choice.
     */
    private fun chainComputerTurns(
        gameId: Uuid,
        movement: MovementResult,
        playersById: Map<Uuid, Player>,
    ): List<TurnEvent> =
        if (movement.events.lastOrNull() is TurnEvent.TurnEnded) {
            playComputerTurns(gameId, movement.updatedGame, playersById)
        } else {
            emptyList()
        }

    /**
     * Plays full turns -- roll and all -- starting from [game]'s current turn, one per computer
     * player, for as long as computer players keep coming up next in turn order -- stopping as soon
     * as it's a human's turn or the game ends. Used both to continue a chain after a human's turn
     * ends and to play out any computer players leading turn order right when a game starts.
     *
     * The moment the chain lands on a human (including a game with only one player, whose "next"
     * turn is their own), it emits [TurnEvent.TurnStarted] for them and stops there -- that human
     * still has to roll themselves, so unlike a computer's turn, nothing else is going to announce
     * whose turn it is. Clients shouldn't have to work that out themselves from turnOrder and
     * turnNumber, especially once anything can reorder whose turn is next.
     */
    private fun playComputerTurns(
        gameId: Uuid,
        game: Game,
        playersById: Map<Uuid, Player>,
    ): List<TurnEvent> {
        val events = mutableListOf<TurnEvent>()
        var current = game

        while (!isGameOver(current)) {
            val turnOrder = current.turnOrder ?: break
            val nextPlayerId = turnOrder[current.turnNumber % turnOrder.size]
            val nextPlayer = playersById[nextPlayerId] ?: break
            if (nextPlayer.userId != null) {
                events += TurnEvent.TurnStarted(nextPlayerId, current.turnNumber)
                break
            }

            // If a computer player's turn can't actually be played (e.g. no
            // path forward), stop the chain here rather than failing
            // whatever triggered it -- the turns already taken are still valid.
            val played = playComputerTurn(gameId, nextPlayerId, current).getOrNull() ?: break
            events += played.events
            current = played.updatedGame
        }

        return events
    }

    private data class PlayedTurn(val events: List<TurnEvent>, val updatedGame: Game)

    private fun playComputerTurn(gameId: Uuid, playerId: Uuid, game: Game): Result<PlayedTurn> {
        val boardGraph =
            boardDao.findById(game.boardId.value)
                ?: return Result.failure(
                    InvalidTurnException("Board for game $gameId no longer exists.")
                )
        val state =
            playerDao.findState(playerId)
                ?: return Result.failure(InvalidPlayerException("Player $playerId has no state."))
        val fromSpaceId =
            state.currentSpaceId?.value
                ?: boardGraph.board.startSpaceId
                ?: return Result.failure(
                    InvalidTurnException("Board for game $gameId has no start space.")
                )

        val roll = dice.roll()
        val events = mutableListOf<TurnEvent>(TurnEvent.DiceRolled(playerId, roll))
        val isComputer = true // this whole function only ever plays a computer player's turn
        val movement =
            advanceMovement(gameId, playerId, isComputer, game, boardGraph, fromSpaceId, roll)
                .getOrElse {
                    return Result.failure(it)
                }
        events += movement.events

        return Result.success(PlayedTurn(events, movement.updatedGame))
    }

    companion object {
        private const val MIN_SHOPS_OWNED_TO_RECALCULATE = 2
        private const val MIN_STOCK_TRADE_QUANTITY = 1
        private const val MAX_STOCK_TRADE_QUANTITY = 99

        // Above this many shares in one buy or sell, the trade also moves the district's stock
        // price -- see [fluctuateStockPrice].
        private const val STOCK_FLUCTUATION_THRESHOLD = 10

        // A trade's price fluctuation moves current_stock_value by this fraction of its own
        // pre-trade value, rounded down, plus 1 -- see [fluctuateStockPrice].
        private const val STOCK_FLUCTUATION_DIVISOR = 16

        // Intermediate scale used only while dividing to compute an average -- rounded away again
        // once the result is derived, so this just needs to be generous enough not to lose
        // precision along the way. See [averageStockValue].
        private const val STOCK_AVERAGE_INTERMEDIATE_SCALE = 10
        private val SUIT_SPACE_TYPES =
            setOf(SpaceType.HEART, SpaceType.DIAMOND, SpaceType.SPADE, SpaceType.CLUB)
    }
}
