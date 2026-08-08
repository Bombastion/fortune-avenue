package com.fortuneavenue.server.service

import com.fortuneavenue.server.dao.GameDao
import com.fortuneavenue.server.models.game.db.Game
import org.springframework.stereotype.Service
import kotlin.uuid.Uuid

@Service
class GameService(
	private val gameDao: GameDao,
) {

	fun createGame(): Game = gameDao.create()

	fun getGame(id: Uuid): Game? = gameDao.findById(id)
}
