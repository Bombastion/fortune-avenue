package com.fortuneavenue.server.service

import com.fortuneavenue.server.dao.BoardDao
import com.fortuneavenue.server.dao.GameDao
import com.fortuneavenue.server.dao.PlayerDao
import com.fortuneavenue.server.models.player.db.PlayerStatus
import org.springframework.stereotype.Service
import kotlin.uuid.Uuid

/**
 * Owns the actual flow of a game: readying up, deciding turn order once
 * everyone's ready, and playing out turns. Movement is deliberately simple
 * for now -- one space forward per turn, always following the lowest
 * branchOrder path out of a space -- since there's no dice roll or player
 * choice of branch yet.
 *
 * A "turn" here is one player's move, not a full round: turnNumber counts
 * up by one per move and cycles through turnOrder via `turnNumber % size`,
 * so with 3 players, turns 0/1/2 are their first moves, 3/4/5 their second,
 * and so on. The game ends once turnNumber reaches maxTurns.
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

		/** This was the last player needed -- turn order's been decided and the game has started. */
		data class GameStarted(val turnOrder: List<Uuid>) : ReadyOutcome
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

		val allReady = players.all { player ->
			val status = if (player.id.value == playerId) PlayerStatus.READY else playerDao.findState(player.id.value)?.status
			status == PlayerStatus.READY
		}
		if (!allReady) return Result.success(ReadyOutcome.Waiting)

		val turnOrder = players.map { it.id.value }.shuffled()
		gameDao.startGame(gameId, turnOrder)

		return Result.success(ReadyOutcome.GameStarted(turnOrder))
	}

	fun takeTurn(gameId: Uuid, playerId: Uuid): Result<TurnResult> {
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
			TurnResult(
				turnNumber = game.turnNumber,
				playerId = playerId,
				fromSpaceId = fromSpaceId,
				toSpaceId = nextPath.toSpaceId.value,
				gameOver = updatedGame.turnNumber >= updatedGame.maxTurns,
			),
		)
	}
}
