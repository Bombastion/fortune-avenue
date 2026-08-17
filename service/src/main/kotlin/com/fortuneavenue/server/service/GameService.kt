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

    fun createGame(boardId: Uuid): Result<Game> {
        if (boardDao.findById(boardId) == null) {
            return Result.failure(InvalidGameException("Board $boardId does not exist."))
        }

        return Result.success(gameDao.create(boardId))
    }

    fun getGame(id: Uuid): Game? = gameDao.findById(id)
}
