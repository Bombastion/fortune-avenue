package com.fortuneavenue.server.service

import com.fortuneavenue.server.dao.BoardDao
import com.fortuneavenue.server.dao.GameDao
import com.fortuneavenue.server.models.game.db.Game
import org.springframework.stereotype.Service
import kotlin.uuid.Uuid

@Service
class GameService(
	private val gameDao: GameDao,
	private val boardDao: BoardDao,
) {

	/**
	 * boardId comes from the request body (not the URL), so an unknown board
	 * is treated the same way an unknown userId is for players: a 400-shaped
	 * validation failure on what's being created, not a 404.
	 */
	fun createGame(boardId: Uuid): Result<Game> {
		if (boardDao.findById(boardId) == null) {
			return Result.failure(InvalidGameException("Board $boardId does not exist."))
		}

		return Result.success(gameDao.create(boardId))
	}

	fun getGame(id: Uuid): Game? = gameDao.findById(id)
}
