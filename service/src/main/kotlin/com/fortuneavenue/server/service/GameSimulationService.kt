package com.fortuneavenue.server.service

import com.fortuneavenue.server.dao.BoardDao
import com.fortuneavenue.server.dao.GameDao
import com.fortuneavenue.server.dao.GameDistrictInformationDao
import com.fortuneavenue.server.dao.GameShopInformationDao
import com.fortuneavenue.server.dao.PlayerDao
import com.fortuneavenue.server.models.board.db.BoardGraph
import com.fortuneavenue.server.models.board.db.BoardPath
import com.fortuneavenue.server.models.board.db.GameShopInformation
import com.fortuneavenue.server.models.board.db.SpaceType
import com.fortuneavenue.server.models.game.db.Game
import com.fortuneavenue.server.models.player.db.Player
import com.fortuneavenue.server.models.player.db.PlayerStatus
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.uuid.Uuid

/**
 * Owns the actual flow of a game: readying up, deciding turn order once
 * everyone's ready, and playing out turns.
 *
 * A turn starts with a die roll, which sets how many spaces the current
 * player has left to move. They're moved forward automatically, one space
 * at a time, decrementing remaining movement, for as long as the
 * space they're on only has one path out of it. The moment they land
 * somewhere with more than one outgoing path, movement pauses: a human
 * player has to choose which branch to take (see [choosePath]) before
 * moving continues, while a computer player picks right away (see
 * [ComputerPlayer]) and keeps going without ever pausing. If movement
 * instead runs out on an unowned SHOP space, a human player is offered the
 * chance to buy it (see [buyShop]/[declineShopPurchase]) before the turn
 * actually ends, while a computer player decides whether it wants to right
 * away, given its actual current gold (again see
 * [ComputerPlayer.shouldBuyShop]). Either way, a purchase
 * always requires enough gold on hand up front -- [buyShop] fails outright
 * for a human short on gold, and a computer player wanting a shop it can't
 * afford is simply treated the same as it not wanting one. Gold can still
 * go negative *after* a purchase, just from other causes not yet
 * implemented. The turn ends once
 * movement reaches zero, at
 * which point play moves to the next player in turn order. This is announced
 * with a [TurnEvent.TurnStarted] the moment that next player is a human,
 * since nothing else is going to happen until they roll themselves.
 * The game ends once turnNumber reaches maxTurns.
 *
 * Every space a player is moved onto along the way, whether just passed through mid-move or
 * where they end up, is also checked for a suit (HEART/DIAMOND/SPADE/CLUB, see SpaceType):
 * landing on or passing one picks it up for that player (see PlayerDao.addHeldSuit), announced
 * with a [TurnEvent.SuitPickedUp] the first time, and silently ignored every time after.
 *
 * The same is true of a BANK space, but the other direction: passing or landing on one while
 * currently holding all 4 suits triggers a promotion (see PlayerDao.clearHeldSuitsIfComplete),
 * clearing every suit the player holds and announcing it with a [TurnEvent.Promoted]. A BANK
 * space is otherwise a no-op -- nothing happens visiting one without every suit in hand.
 *
 * A player with no [com.fortuneavenue.server.models.player.db.Player.userId]
 * is a computer opponent -- there's nobody connected who could ready it up
 * or take its turns, so this service does that on its behalf: computer
 * players are auto-readied once every human is, and their turns are played
 * out automatically (roll and all) as soon as it's their turn
 */
@Service
class GameSimulationService(
	private val gameDao: GameDao,
	private val playerDao: PlayerDao,
	private val boardDao: BoardDao,
	private val gameShopInformationDao: GameShopInformationDao,
	private val gameDistrictInformationDao: GameDistrictInformationDao,
	private val dice: Dice,
	private val computerPlayer: ComputerPlayer,
) {

	sealed interface ReadyOutcome {
		/** Marked ready, but not every player is (or the game already started). */
		data object Waiting : ReadyOutcome

		/**
		 * This was the last player needed -- turn order's been decided and
		 * the game has started. [openingTurnEvents] is whatever happened
		 * automatically because one or more computer players led that turn
		 * order (their full turns played out, one after another), followed
		 * by a [TurnEvent.TurnStarted] for whichever human ends up first in
		 * line once they're done -- or just that one event, if a human leads
		 * turn order to begin with.
		 */
		data class GameStarted(
			val turnOrder: List<Uuid>,
			val openingTurnEvents: List<TurnEvent> = emptyList(),
		) : ReadyOutcome
	}

	/** One outgoing path a player can choose when paused at a branch. */
	data class PathOption(val toSpaceId: Uuid, val branchOrder: Int)

	/** Everything that can happen in the course of rolling, moving, and (eventually) ending a turn. */
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
		 * (HEART/DIAMOND/SPADE/CLUB) -- triggering a promotion that clears every suit from their
		 * inventory. Not emitted for a BANK space visited without every suit already held.
		 */
		data class Promoted(
			override val playerId: Uuid,
			val spaceId: Uuid,
		) : TurnEvent

		/** Movement is paused on [spaceId] until [choosePath] is called with one of [options]. */
		data class ChoiceRequired(
			override val playerId: Uuid,
			val spaceId: Uuid,
			val options: List<PathOption>,
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
		 * Emitted right after a [ShopPurchased] that brought [playerId]'s owned count in
		 * [districtId] to 2 or more -- every shop they own there (including the one just
		 * bought) has been recalculated per that district's progression.
		 * [newValuesBySpaceId] maps each affected shop's spaceId to its new currentValue.
		 */
		data class DistrictValuesRecalculated(
			override val playerId: Uuid,
			val districtId: Uuid,
			val newValuesBySpaceId: Map<Uuid, Int>,
		) : TurnEvent

		data class TurnEnded(
			override val playerId: Uuid,
			val turnNumber: Int,
			val gameOver: Boolean,
		) : TurnEvent

		/**
		 * It's now [playerId]'s turn, and nothing is going to happen on their
		 * behalf -- they need to roll. Only ever emitted for a human; a
		 * computer player's turn gets played out immediately instead (see
		 * [DiceRolled]), so there's nothing to announce ahead of it.
		 */
		data class TurnStarted(
			override val playerId: Uuid,
			val turnNumber: Int,
		) : TurnEvent
	}

	fun markReady(gameId: Uuid, playerId: Uuid): Result<ReadyOutcome> {
		val game = gameDao.findById(gameId)
			?: return Result.failure(GameNotFoundException("Game $gameId does not exist."))
		val players = playerDao.findByGameId(gameId)
		if (players.none { it.id.value == playerId }) {
			return Result.failure(InvalidPlayerException("Player $playerId is not in game $gameId."))
		}

		playerDao.updateStatus(playerId, PlayerStatus.READY)

		// Turn order is only ever decided once -- a player readying up again
		// after the game has already started (e.g. on reconnect) shouldn't
		// re-shuffle it.
		if (game.turnOrder != null) return Result.success(ReadyOutcome.Waiting)

		val (computerPlayers, humanPlayers) = players.partition { it.userId == null }
		val allHumansReady = humanPlayers.all { player ->
			val status = if (player.id.value == playerId) PlayerStatus.READY else playerDao.findState(player.id.value)?.status
			status == PlayerStatus.READY
		}
		if (!allHumansReady) return Result.success(ReadyOutcome.Waiting)

		// Nobody's connected to ready a computer player up themselves -- now
		// that every human is ready, do it for them so the game can start.
		computerPlayers.forEach { playerDao.updateStatus(it.id.value, PlayerStatus.READY) }

		val turnOrder = players.map { it.id.value }.shuffled()
		val startedGame = gameDao.startGame(gameId, turnOrder)

		// The game only ever starts once (guarded by the turnOrder check above), so this is the
		// one moment a per-game copy of the board's shops (and each district's stock) needs to be
		// seeded -- see GameShopInformationDao.seedForGame and GameDistrictInformationDao.seedForGame.
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
		val openingTurnEvents = startedGame?.let { playComputerTurns(gameId, it, playersById) }.orEmpty()

		return Result.success(ReadyOutcome.GameStarted(turnOrder, openingTurnEvents))
	}

	/**
	 * Rolls the die for [playerId]'s turn and moves them forward that many
	 * spaces -- automatically, one at a time, until either movement runs out
	 * (ending the turn), a branch is reached and paused on ([choosePath]
	 * picks up from there), a purchase decision is reached and paused on
	 * ([buyShop]/[declineShopPurchase] picks up from there), or the game
	 * ends. Whichever computer players immediately follow in turn order then
	 * get their own full turns played out the same way, stopping once it's a
	 * human's turn again or the game ends. The result is always at least one
	 * event (the roll) and is in order, so the caller can report each one
	 * (e.g. as a broadcast per entry) as it happens.
	 */
	fun rollDice(gameId: Uuid, playerId: Uuid): Result<List<TurnEvent>> {
		val game = currentTurnGame(gameId, playerId).getOrElse { return Result.failure(it) }
		if (game.currentMovementPoints != null) {
			return Result.failure(InvalidTurnException("Player $playerId already rolled this turn -- choose a path to continue."))
		}

		val playersById = playerDao.findByGameId(gameId).associateBy { it.id.value }
		val player = playersById[playerId]
			?: return Result.failure(InvalidPlayerException("Player $playerId is not in game $gameId."))
		val boardGraph = boardDao.findById(game.boardId.value)
			?: return Result.failure(InvalidTurnException("Board for game $gameId no longer exists."))
		val state = playerDao.findState(playerId)
			?: return Result.failure(InvalidPlayerException("Player $playerId has no state."))
		val fromSpaceId = state.currentSpaceId?.value ?: boardGraph.board.startSpaceId
			?: return Result.failure(InvalidTurnException("Board for game $gameId has no start space."))

		val roll = dice.roll()
		val events = mutableListOf<TurnEvent>(TurnEvent.DiceRolled(playerId, roll))
		val movement = advanceMovement(gameId, playerId, player.userId == null, game, boardGraph, fromSpaceId, roll)
			.getOrElse { return Result.failure(it) }
		events += movement.events
		events += chainComputerTurns(gameId, movement, playersById)

		return Result.success(events)
	}

	/**
	 * Resumes [playerId]'s turn after it paused on a branch, moving them
	 * onto [toSpaceId] -- which must be one of the options the pause offered
	 * -- and continuing movement (and any following computer players' full
	 * turns) exactly as [rollDice] does.
	 */
	fun choosePath(gameId: Uuid, playerId: Uuid, toSpaceId: Uuid): Result<List<TurnEvent>> {
		val game = currentTurnGame(gameId, playerId).getOrElse { return Result.failure(it) }
		val remaining = game.currentMovementPoints
			?: return Result.failure(InvalidTurnException("Player $playerId hasn't rolled the dice yet."))

		val playersById = playerDao.findByGameId(gameId).associateBy { it.id.value }
		val player = playersById[playerId]
			?: return Result.failure(InvalidPlayerException("Player $playerId is not in game $gameId."))
		val boardGraph = boardDao.findById(game.boardId.value)
			?: return Result.failure(InvalidTurnException("Board for game $gameId no longer exists."))
		val state = playerDao.findState(playerId)
			?: return Result.failure(InvalidPlayerException("Player $playerId has no state."))
		val currentSpaceId = state.currentSpaceId?.value ?: boardGraph.board.startSpaceId
			?: return Result.failure(InvalidTurnException("Board for game $gameId has no start space."))

		val outgoing = boardGraph.paths.filter { it.fromSpaceId.value == currentSpaceId }
		val chosenPath = outgoing.find { it.toSpaceId.value == toSpaceId }
			?: return Result.failure(InvalidTurnException("$toSpaceId isn't a path out of player $playerId's current space."))

		val movementPointsRemaining = remaining - 1
		val events = applyMove(playerId, game.turnNumber, currentSpaceId, chosenPath, movementPointsRemaining, boardGraph).toMutableList()
		val movement = advanceMovement(
			gameId,
			playerId,
			player.userId == null,
			game,
			boardGraph,
			chosenPath.toSpaceId.value,
			movementPointsRemaining,
		).getOrElse { return Result.failure(it) }
		events += movement.events
		events += chainComputerTurns(gameId, movement, playersById)

		return Result.success(events)
	}

	/**
	 * Buys the shop [playerId] is currently paused on (see
	 * [TurnEvent.ShopPurchaseAvailable]) for its current price, deducted from their gold --
	 * which can go negative afterward from other causes not yet implemented, see
	 * PlayerStatesTable.currentGold, but a purchase itself always requires enough gold on hand
	 * up front; fails if it doesn't. If this purchase brings the player's owned count in that
	 * shop's district to 2 or more, every shop they own there (including the one just bought)
	 * is recalculated per that district's progression (see DistrictValueProgressionsTable).
	 * Ends the turn afterward and chains into any following computer players' turns, exactly as
	 * [rollDice]/[choosePath] do.
	 */
	fun buyShop(gameId: Uuid, playerId: Uuid): Result<List<TurnEvent>> {
		val (game, shop, playersById) = pendingShopPurchase(gameId, playerId).getOrElse { return Result.failure(it) }
		val gold = currentGold(playerId)
			?: return Result.failure(InvalidPlayerException("Player $playerId has no state."))
		if (gold < shop.currentValue) {
			return Result.failure(
				InvalidTurnException(
					"Player $playerId can't afford this shop -- it costs ${shop.currentValue} but they only have $gold gold.",
				),
			)
		}

		val movement = endTurn(gameId, playerId, game, purchaseShop(gameId, playerId, shop))
			.getOrElse { return Result.failure(it) }
		val events = movement.events + chainComputerTurns(gameId, movement, playersById)

		return Result.success(events)
	}

	/** Declines the pending purchase from [TurnEvent.ShopPurchaseAvailable] and ends the turn without buying. */
	fun declineShopPurchase(gameId: Uuid, playerId: Uuid): Result<List<TurnEvent>> {
		val (game, _, playersById) = pendingShopPurchase(gameId, playerId).getOrElse { return Result.failure(it) }

		val movement = endTurn(gameId, playerId, game, emptyList()).getOrElse { return Result.failure(it) }
		val events = movement.events + chainComputerTurns(gameId, movement, playersById)

		return Result.success(events)
	}

	/** Validates that it's actually [playerId]'s turn to act right now, returning the game if so. */
	private fun currentTurnGame(gameId: Uuid, playerId: Uuid): Result<Game> {
		val game = gameDao.findById(gameId)
			?: return Result.failure(GameNotFoundException("Game $gameId does not exist."))
		val turnOrder = game.turnOrder
			?: return Result.failure(InvalidTurnException("Game $gameId hasn't started yet -- not everyone is ready."))
		if (game.turnNumber >= game.maxTurns) {
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
	 * exactly like it is for a branch choice: 0 specifically (rather than null, or >0 for a
	 * branch) means movement finished but the turn hasn't ended yet, waiting on this decision.
	 */
	private fun pendingShopPurchase(gameId: Uuid, playerId: Uuid): Result<Triple<Game, GameShopInformation, Map<Uuid, Player>>> {
		val game = currentTurnGame(gameId, playerId).getOrElse { return Result.failure(it) }
		if (game.currentMovementPoints != 0) {
			return Result.failure(InvalidTurnException("Player $playerId has no shop purchase decision pending."))
		}

		val state = playerDao.findState(playerId)
			?: return Result.failure(InvalidPlayerException("Player $playerId has no state."))
		val spaceId = state.currentSpaceId?.value
			?: return Result.failure(InvalidTurnException("Player $playerId has no current space."))
		val shop = gameShopInformationDao.findByGameAndSpace(gameId, spaceId)?.takeIf { it.ownerId == null }
			?: return Result.failure(InvalidTurnException("There's no shop purchase pending for player $playerId."))

		val playersById = playerDao.findByGameId(gameId).associateBy { it.id.value }
		return Result.success(Triple(game, shop, playersById))
	}

	private data class MovementResult(val events: List<TurnEvent>, val updatedGame: Game)

	/**
	 * Moves a player forward from [startingSpaceId] with [startingMovementPoints]
	 * left to spend -- one space at a time -- stopping in one of four ways:
	 * movement runs out on an ordinary space (the turn ends), it runs out on
	 * an unowned shop (a human pauses for a purchase decision; a computer
	 * decides immediately and the turn ends), a computer player is moving
	 * and hits a branch (picks randomly and keeps going, so pausing never
	 * actually happens for one), or a human player hits a branch (pauses
	 * here, persisting the remaining movement so a later [choosePath] call,
	 * even after a reconnect, can pick up from it).
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
				return Result.failure(InvalidTurnException("No path forward from player $playerId's current space."))
			}

			if (outgoing.size > 1 && !isComputer) {
				gameDao.setMovementPoints(gameId, remaining)
				events += TurnEvent.ChoiceRequired(
					playerId = playerId,
					spaceId = currentSpaceId,
					options = outgoing.sortedBy { it.branchOrder }.map { PathOption(it.toSpaceId.value, it.branchOrder) },
				)
				return Result.success(MovementResult(events, game))
			}

			val chosenPath = if (outgoing.size > 1) computerPlayer.chooseBranch(outgoing) else outgoing.single()
			remaining -= 1
			events += applyMove(playerId, game.turnNumber, currentSpaceId, chosenPath, remaining, boardGraph)
			currentSpaceId = chosenPath.toSpaceId.value
		}

		val unownedShop = gameShopInformationDao.findByGameAndSpace(gameId, currentSpaceId)?.takeIf { it.ownerId == null }
		if (unownedShop != null) {
			if (!isComputer) {
				gameDao.setMovementPoints(gameId, 0)
				events += TurnEvent.ShopPurchaseAvailable(playerId, currentSpaceId, unownedShop.currentValue)
				return Result.success(MovementResult(events, game))
			}

			// ComputerPlayer decides whether it *wants* the shop, given its real currentGold to
			// weigh -- but affordability itself is still enforced here, same floor a human's
			// buyShop gets, regardless of what shouldBuyShop says (see its doc).
			if (computerPlayer.shouldBuyShop(unownedShop, currentGold(playerId) ?: 0) && canAfford(playerId, unownedShop.currentValue)) {
				events += purchaseShop(gameId, playerId, unownedShop)
			}
		}

		return endTurn(gameId, playerId, game, events)
	}

	/** Advances turnNumber and appends [TurnEvent.TurnEnded] to [precedingEvents] -- the shared tail of every way a turn can finish. */
	private fun endTurn(gameId: Uuid, playerId: Uuid, game: Game, precedingEvents: List<TurnEvent>): Result<MovementResult> {
		val updatedGame = gameDao.advanceTurn(gameId)
			?: return Result.failure(GameNotFoundException("Game $gameId does not exist."))
		val events = precedingEvents + TurnEvent.TurnEnded(playerId, game.turnNumber, gameOver = updatedGame.turnNumber >= updatedGame.maxTurns)
		return Result.success(MovementResult(events, updatedGame))
	}

	/**
	 * Pays [shop]'s current price out of [playerId]'s gold and hands them ownership, then -- if
	 * that brought their owned count in [shop]'s district to 2 or more -- recalculates every
	 * shop they own there per that district's progression (see DistrictValueProgressionsTable):
	 * every shop they already owned gets existingShopBoostPercentage, and the one just bought
	 * gets newShopBoostPercentage instead. Shops outside a district, or a purchase that's still
	 * the player's only shop in one, have nothing to recalculate -- and since current_stock_value
	 * is derived purely from shops' currentValue (see GameDistrictInformationDao), a district's
	 * stock only ever needs recomputing in lockstep with that same recalculation.
	 */
	private fun purchaseShop(gameId: Uuid, playerId: Uuid, shop: GameShopInformation): List<TurnEvent> {
		val price = shop.currentValue
		playerDao.adjustGold(playerId, -price)
		gameShopInformationDao.setOwner(shop.id.value, playerId)

		val events = mutableListOf<TurnEvent>(TurnEvent.ShopPurchased(playerId, shop.spaceId.value, price))

		val districtId = shop.districtId
		if (districtId != null) {
			val ownedInDistrict = gameShopInformationDao.findOwnedByPlayerInDistrict(gameId, playerId, districtId)
			val progression = if (ownedInDistrict.size >= MIN_SHOPS_OWNED_TO_RECALCULATE) {
				boardDao.findDistrictValueProgression(districtId, ownedInDistrict.size)
			} else {
				null
			}

			if (progression != null) {
				val newValuesBySpaceId = ownedInDistrict.associate { owned ->
					val percentage = if (owned.id.value == shop.id.value) {
						progression.newShopBoostPercentage
					} else {
						progression.existingShopBoostPercentage
					}
					val newValue = boosted(owned.currentValue, percentage)
					gameShopInformationDao.setCurrentValue(owned.id.value, newValue)
					owned.spaceId.value to newValue
				}
				events += TurnEvent.DistrictValuesRecalculated(playerId, districtId.value, newValuesBySpaceId)

				// current_stock_value averages every shop in the district, not just the ones
				// [ownedInDistrict] just boosted -- shops other players (or nobody) own there
				// still count, so this re-reads the whole district rather than reusing that list.
				val allShopsInDistrict = gameShopInformationDao.findByGameAndDistrict(gameId, districtId)
				gameDistrictInformationDao.recalculateCurrentStockValue(gameId, districtId, allShopsInDistrict)
			}
		}

		return events
	}

	private fun boosted(value: Int, percentage: BigDecimal): Int =
		(BigDecimal(value) * (BigDecimal.ONE + percentage)).setScale(0, RoundingMode.HALF_UP).toInt()

	/** Null only if [playerId] somehow has no state at all -- see [PlayerDao.findState]. */
	private fun currentGold(playerId: Uuid): Int? = playerDao.findState(playerId)?.currentGold

	/** Whether [playerId] currently has at least [price] gold on hand -- a missing state counts as no. */
	private fun canAfford(playerId: Uuid, price: Int): Boolean = (currentGold(playerId) ?: 0) >= price

	/**
	 * Moves [playerId] onto [path]'s destination and returns the resulting events -- always a
	 * leading [TurnEvent.Moved], plus a [TurnEvent.SuitPickedUp] if that destination is a suit
	 * space and picking it up was actually new (see [pickUpSuit]), plus a [TurnEvent.Promoted] if
	 * it's a BANK space reached while holding all 4 suits (see [checkPromotion]). Every space a
	 * player is moved onto over the course of a turn -- passed through mid-move or landed on at
	 * the end -- flows through here exactly once
	 */
	private fun applyMove(
		playerId: Uuid,
		turnNumber: Int,
		fromSpaceId: Uuid,
		path: BoardPath,
		movementPointsRemaining: Int,
		boardGraph: BoardGraph,
	): List<TurnEvent> {
		playerDao.updatePosition(playerId, path.toSpaceId.value)
		val moved = TurnEvent.Moved(playerId, turnNumber, fromSpaceId, path.toSpaceId.value, movementPointsRemaining)
		return listOf(moved) +
			pickUpSuit(playerId, path.toSpaceId.value, boardGraph) +
			checkPromotion(playerId, path.toSpaceId.value, boardGraph)
	}

	/**
	 * [spaceId] is picked up as a suit for [playerId] if it's a HEART/DIAMOND/SPADE/CLUB space
	 * (see [SUIT_SPACE_TYPES]) -- a no-op, emitting nothing, for any other space type or one
	 * [playerId] already holds (see [PlayerDao.addHeldSuit]).
	 */
	private fun pickUpSuit(playerId: Uuid, spaceId: Uuid, boardGraph: BoardGraph): List<TurnEvent> {
		val suit = boardGraph.spaces.find { it.id.value == spaceId }?.spaceType?.takeIf { it in SUIT_SPACE_TYPES }
			?: return emptyList()
		val pickedUp = playerDao.addHeldSuit(playerId, suit) ?: return emptyList()

		return if (pickedUp) listOf(TurnEvent.SuitPickedUp(playerId, spaceId, suit)) else emptyList()
	}

	/**
	 * [spaceId] triggers a promotion for [playerId] if it's a BANK space (see [SpaceType]) and
	 * they currently hold all 4 suits (see [PlayerDao.clearHeldSuitsIfComplete]) -- clearing
	 * their held suits entirely. A no-op, emitting nothing, for any other space type or a player
	 * who doesn't hold every suit yet.
	 */
	private fun checkPromotion(playerId: Uuid, spaceId: Uuid, boardGraph: BoardGraph): List<TurnEvent> {
		boardGraph.spaces.find { it.id.value == spaceId }?.spaceType?.takeIf { it == SpaceType.BANK }
			?: return emptyList()
		val promoted = playerDao.clearHeldSuitsIfComplete(playerId, SUIT_SPACE_TYPES) ?: return emptyList()

		return if (promoted) listOf(TurnEvent.Promoted(playerId, spaceId)) else emptyList()
	}

	/** Only chains into the next player(s) once [movement] actually ended the turn rather than pausing on a choice. */
	private fun chainComputerTurns(gameId: Uuid, movement: MovementResult, playersById: Map<Uuid, Player>): List<TurnEvent> =
		if (movement.events.lastOrNull() is TurnEvent.TurnEnded) {
			playComputerTurns(gameId, movement.updatedGame, playersById)
		} else {
			emptyList()
		}

	/**
	 * Plays full turns -- roll and all -- starting from [game]'s current
	 * turn, one per computer player, for as long as computer players keep
	 * coming up next in turn order -- stopping as soon as it's a human's
	 * turn or the game ends. Used both to continue a chain after a human's
	 * turn ends and to play out any computer players leading turn order
	 * right when a game starts.
	 *
	 * The moment the chain lands on a human (including a game with only one
	 * player, whose "next" turn is their own), it emits [TurnEvent.TurnStarted]
	 * for them and stops there -- that human still has to roll themselves,
	 * so unlike a computer's turn, nothing else is going to announce whose
	 * turn it is. Clients shouldn't have to work that out themselves from
	 * turnOrder and turnNumber, especially once anything can reorder whose
	 * turn is next.
	 */
	private fun playComputerTurns(gameId: Uuid, game: Game, playersById: Map<Uuid, Player>): List<TurnEvent> {
		val events = mutableListOf<TurnEvent>()
		var current = game

		while (current.turnNumber < current.maxTurns) {
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
		val boardGraph = boardDao.findById(game.boardId.value)
			?: return Result.failure(InvalidTurnException("Board for game $gameId no longer exists."))
		val state = playerDao.findState(playerId)
			?: return Result.failure(InvalidPlayerException("Player $playerId has no state."))
		val fromSpaceId = state.currentSpaceId?.value ?: boardGraph.board.startSpaceId
			?: return Result.failure(InvalidTurnException("Board for game $gameId has no start space."))

		val roll = dice.roll()
		val events = mutableListOf<TurnEvent>(TurnEvent.DiceRolled(playerId, roll))
		val isComputer = true // this whole function only ever plays a computer player's turn
		val movement = advanceMovement(gameId, playerId, isComputer, game, boardGraph, fromSpaceId, roll)
			.getOrElse { return Result.failure(it) }
		events += movement.events

		return Result.success(PlayedTurn(events, movement.updatedGame))
	}

	companion object {
		private const val MIN_SHOPS_OWNED_TO_RECALCULATE = 2
		private val SUIT_SPACE_TYPES = setOf(SpaceType.HEART, SpaceType.DIAMOND, SpaceType.SPADE, SpaceType.CLUB)
	}
}
