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
     * player's net worth reaches or exceeds it (see GameSimulationService). Omit to keep
     * games.target_net_worth's own default of 6000 (see GamesTable).
     */
    fun createGame(boardId: Uuid, targetNetWorth: Int? = null): Result<Game> {
        if (boardDao.findById(boardId) == null) {
            return Result.failure(InvalidGameException("Board $boardId does not exist."))
        }
        if (targetNetWorth != null && targetNetWorth <= 0) {
            return Result.failure(
                InvalidGameException("targetNetWorth must be a positive integer.")
            )
        }

        return Result.success(gameDao.create(boardId, targetNetWorth))
    }

    fun getGame(id: Uuid): Game? = gameDao.findById(id)
}
