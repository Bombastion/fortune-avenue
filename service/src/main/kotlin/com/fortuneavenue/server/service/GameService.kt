package com.fortuneavenue.server.service

import com.fortuneavenue.server.dao.BoardDao
import com.fortuneavenue.server.dao.GameDao
import com.fortuneavenue.server.models.game.db.Game
import kotlin.uuid.Uuid
import org.springframework.stereotype.Service

@Service
class GameService(
    private val gameDao: GameDao,
    private val boardDao: BoardDao,
) {

    /**
     * [targetNetWorth], if given, must be a positive integer -- the game ends the moment any
     * player's net worth reaches or exceeds it (see GameSimulationService). Omit to default to
     * 6000 gold -- this is the one place that default lives; it isn't a DB default on GamesTable,
     * and GameDao.create requires the resolved value explicitly.
     *
     * [maxTurns], if given, must also be a positive integer -- the game ends once turnNumber
     * reaches it, whichever happens first against targetNetWorth (see
     * GameSimulationService.isGameOver). Omit to default to 10 turns.
     */
    fun createGame(boardId: Uuid, targetNetWorth: Int? = null, maxTurns: Int? = null): Result<Game> {
        if (boardDao.findById(boardId) == null) {
            return Result.failure(InvalidGameException("Board $boardId does not exist."))
        }
        if (targetNetWorth != null && targetNetWorth <= 0) {
            return Result.failure(
                InvalidGameException("targetNetWorth must be a positive integer.")
            )
        }
        if (maxTurns != null && maxTurns <= 0) {
            return Result.failure(InvalidGameException("maxTurns must be a positive integer."))
        }

        return Result.success(gameDao.create(boardId, targetNetWorth ?: 6000, maxTurns ?: 10))
    }

    fun getGame(id: Uuid): Game? = gameDao.findById(id)
}
