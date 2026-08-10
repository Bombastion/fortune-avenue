package com.fortuneavenue.server.service

import com.fortuneavenue.server.dao.BoardDao
import com.fortuneavenue.server.dao.GameDao
import com.fortuneavenue.server.dao.PlayerDao
import com.fortuneavenue.server.models.game.db.Game
import com.fortuneavenue.server.models.player.db.Player
import com.fortuneavenue.server.models.player.db.PlayerStatus
import org.springframework.stereotype.Service
import kotlin.uuid.Uuid

/**
 * Owns the actual flow of a game: readying up, deciding turn order once
 * everyone's ready, and playing out turns. Movement is deliberately simple
 * for now -- one space forward per turn, always following the lowest
 * branchOrder path out of a space -- since there's no dice roll or player
 * choice of branch yet.
 * The game ends once turnNumber reaches maxTurns.
 *
 * A player with no [com.fortuneavenue.server.models.player.db.Player.userId]
 * is a computer opponent -- there's nobody connected who could ready it up
 * or take its turns, so this service does that on its behalf: computer
 * players are auto-readied once every human is, and their turns are played
 * out automatically as soon as it's their turn -- including right when the
 * game starts, if one or more of them land at the front of turn order, so
 * the game can never get stuck waiting on a human who was never first.
 */
@Service
class GameSimulationService(
	private val gameDao: GameDao,
	private val playerDao: PlayerDao,
	private val boardDao: BoardDao,
) {

	sealed interface ReadyOutcome {
		/** Marked ready, but not every player is (or the game already started). */
		data object Waiting : ReadyOutcome

		/**
		 * This was the last player needed -- turn order's been decided and
		 * the game has started. [openingComputerTurns] is any computer
		 * players' turns that got played automatically because they were
		 * first (or led a run of several) in that turn order -- empty if a
		 * human leads it off instead.
		 */
		data class GameStarted(
			val turnOrder: List<Uuid>,
			val openingComputerTurns: List<TurnResult> = emptyList(),
		) : ReadyOutcome
	}

	data class TurnResult(
		val turnNumber: Int,
		val playerId: Uuid,
		val fromSpaceId: Uuid?,
		val toSpaceId: Uuid,
		val gameOver: Boolean,
	)

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
		// nobody's ever going to send a take_turn to kick things off for
		// them -- play those turns out right now so the game doesn't stall
		// before a human even gets a chance to move.
		val playersById = players.associateBy { it.id.value }
		val openingComputerTurns = startedGame?.let { playComputerTurns(gameId, it, playersById) }.orEmpty()

		return Result.success(ReadyOutcome.GameStarted(turnOrder, openingComputerTurns))
	}

	/**
	 * Plays [playerId]'s turn, then keeps playing automatically on behalf of
	 * however many computer players immediately follow in turn order --
	 * stopping once it's a human's turn again or the game ends. The result
	 * list is always at least one element (the requested turn) and is in
	 * turn order, so the caller can report each one (e.g. as a broadcast per
	 * entry) same as it would a single turn.
	 */
	fun takeTurn(gameId: Uuid, playerId: Uuid): Result<List<TurnResult>> {
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

		val playersById = playerDao.findByGameId(gameId).associateBy { it.id.value }

		val firstTurn = playTurn(gameId, playerId, game).getOrElse { return Result.failure(it) }
		val results = mutableListOf(firstTurn.result)
		results += playComputerTurns(gameId, firstTurn.updatedGame, playersById)

		return Result.success(results)
	}

	/**
	 * Plays turns starting from [game]'s current turn, one per computer
	 * player, for as long as computer players keep coming up next in turn
	 * order -- stopping as soon as it's a human's turn or the game ends.
	 * Used both to continue a chain after a human's [takeTurn] and to play
	 * out any computer players leading turn order right when a game starts.
	 */
	private fun playComputerTurns(gameId: Uuid, game: Game, playersById: Map<Uuid, Player>): List<TurnResult> {
		val results = mutableListOf<TurnResult>()
		var current = game

		while (current.turnNumber < current.maxTurns) {
			val turnOrder = current.turnOrder ?: break
			val nextPlayerId = turnOrder[current.turnNumber % turnOrder.size]
			val nextPlayer = playersById[nextPlayerId] ?: break
			if (nextPlayer.userId != null) break

			// If a computer player's turn can't actually be played (e.g. no
			// path forward), stop the chain here rather than failing
			// whatever triggered it -- the turns already taken are still valid.
			val played = playTurn(gameId, nextPlayerId, current).getOrNull() ?: break
			results += played.result
			current = played.updatedGame
		}

		return results
	}

	private data class PlayedTurn(val result: TurnResult, val updatedGame: Game)

	private fun playTurn(gameId: Uuid, playerId: Uuid, game: Game): Result<PlayedTurn> {
		val boardGraph = boardDao.findById(game.boardId.value)
			?: return Result.failure(InvalidTurnException("Board for game $gameId no longer exists."))
		val state = playerDao.findState(playerId)
			?: return Result.failure(InvalidPlayerException("Player $playerId has no state."))

		val fromSpaceId = state.currentSpaceId?.value ?: boardGraph.board.startSpaceId
		val nextPath = fromSpaceId
			?.let { from -> boardGraph.paths.filter { it.fromSpaceId.value == from }.minByOrNull { it.branchOrder } }
			?: return Result.failure(InvalidTurnException("No path forward from player $playerId's current space."))

		playerDao.updatePosition(playerId, nextPath.toSpaceId.value)
		val updatedGame = gameDao.advanceTurn(gameId)
			?: return Result.failure(GameNotFoundException("Game $gameId does not exist."))

		return Result.success(
			PlayedTurn(
				result = TurnResult(
					turnNumber = game.turnNumber,
					playerId = playerId,
					fromSpaceId = fromSpaceId,
					toSpaceId = nextPath.toSpaceId.value,
					gameOver = updatedGame.turnNumber >= updatedGame.maxTurns,
				),
				updatedGame = updatedGame,
			),
		)
	}
}
