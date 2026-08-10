package com.fortuneavenue.server.service

import com.fortuneavenue.server.dao.BoardDao
import com.fortuneavenue.server.dao.GameDao
import com.fortuneavenue.server.dao.PlayerDao
import com.fortuneavenue.server.models.board.db.BoardGraph
import com.fortuneavenue.server.models.board.db.BoardPath
import com.fortuneavenue.server.models.game.db.Game
import com.fortuneavenue.server.models.player.db.Player
import com.fortuneavenue.server.models.player.db.PlayerStatus
import org.springframework.stereotype.Service
import kotlin.uuid.Uuid

/**
 * Owns the actual flow of a game: readying up, deciding turn order once
 * everyone's ready, and playing out turns.
 *
 * A turn starts with a die roll, which sets how many spaces the current
 * player has left to move. They're moved forward automatically -- one space
 * at a time, decrementing that remaining movement -- for as long as the
 * space they're on only has one path out of it. The moment they land
 * somewhere with more than one outgoing path, movement pauses: a human
 * player has to choose which branch to take (see [choosePath]) before
 * moving continues, while a computer player picks randomly right away and
 * keeps going without ever pausing. The turn ends once movement reaches
 * zero with no choice pending, at which point play moves to the next
 * player in turn order.
 * The game ends once turnNumber reaches maxTurns.
 *
 * A player with no [com.fortuneavenue.server.models.player.db.Player.userId]
 * is a computer opponent -- there's nobody connected who could ready it up
 * or take its turns, so this service does that on its behalf: computer
 * players are auto-readied once every human is, and their turns are played
 * out automatically (roll and all) as soon as it's their turn -- including
 * right when the game starts, if one or more of them land at the front of
 * turn order, so the game can never get stuck waiting on a human who was
 * never first.
 */
@Service
class GameSimulationService(
	private val gameDao: GameDao,
	private val playerDao: PlayerDao,
	private val boardDao: BoardDao,
	private val dice: Dice,
) {

	sealed interface ReadyOutcome {
		/** Marked ready, but not every player is (or the game already started). */
		data object Waiting : ReadyOutcome

		/**
		 * This was the last player needed -- turn order's been decided and
		 * the game has started. [openingTurnEvents] is whatever happened
		 * automatically because one or more computer players led that turn
		 * order (their full turns played out, one after another) -- empty
		 * if a human leads it off instead.
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

		/** Movement is paused on [spaceId] until [choosePath] is called with one of [options]. */
		data class ChoiceRequired(
			override val playerId: Uuid,
			val spaceId: Uuid,
			val options: List<PathOption>,
		) : TurnEvent

		data class TurnEnded(
			override val playerId: Uuid,
			val turnNumber: Int,
			val gameOver: Boolean,
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
	 * picks up from there), or the game ends. Whichever computer players
	 * immediately follow in turn order then get their own full turns played
	 * out the same way, stopping once it's a human's turn again or the game
	 * ends. The result is always at least one event (the roll) and is in
	 * order, so the caller can report each one (e.g. as a broadcast per
	 * entry) as it happens.
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
		val events = mutableListOf<TurnEvent>(
			applyMove(playerId, game.turnNumber, currentSpaceId, chosenPath, movementPointsRemaining),
		)
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

	private data class MovementResult(val events: List<TurnEvent>, val updatedGame: Game)

	/**
	 * Moves a player forward from [startingSpaceId] with [startingMovementPoints]
	 * left to spend -- one space at a time -- stopping in one of three ways:
	 * movement runs out (the turn ends), a computer player is moving and hits
	 * a branch (picks randomly and keeps going, so this never actually
	 * happens for one), or a human player hits a branch (pauses here,
	 * persisting the remaining movement so a later [choosePath] call, even
	 * after a reconnect, can pick up from it).
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

			val chosenPath = if (outgoing.size > 1) dice.choose(outgoing) else outgoing.single()
			remaining -= 1
			events += applyMove(playerId, game.turnNumber, currentSpaceId, chosenPath, remaining)
			currentSpaceId = chosenPath.toSpaceId.value
		}

		val updatedGame = gameDao.advanceTurn(gameId)
			?: return Result.failure(GameNotFoundException("Game $gameId does not exist."))
		events += TurnEvent.TurnEnded(playerId, game.turnNumber, gameOver = updatedGame.turnNumber >= updatedGame.maxTurns)
		return Result.success(MovementResult(events, updatedGame))
	}

	private fun applyMove(
		playerId: Uuid,
		turnNumber: Int,
		fromSpaceId: Uuid,
		path: BoardPath,
		movementPointsRemaining: Int,
	): TurnEvent.Moved {
		playerDao.updatePosition(playerId, path.toSpaceId.value)
		return TurnEvent.Moved(playerId, turnNumber, fromSpaceId, path.toSpaceId.value, movementPointsRemaining)
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
	 */
	private fun playComputerTurns(gameId: Uuid, game: Game, playersById: Map<Uuid, Player>): List<TurnEvent> {
		val events = mutableListOf<TurnEvent>()
		var current = game

		while (current.turnNumber < current.maxTurns) {
			val turnOrder = current.turnOrder ?: break
			val nextPlayerId = turnOrder[current.turnNumber % turnOrder.size]
			val nextPlayer = playersById[nextPlayerId] ?: break
			if (nextPlayer.userId != null) break

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
}
