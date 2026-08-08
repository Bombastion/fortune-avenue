package com.fortuneavenue.server.dao

import com.fortuneavenue.server.models.game.db.Game
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository
import kotlin.uuid.Uuid

@Repository
class GameDao {

	fun create(): Game = transaction {
		Game.new { }
	}

	fun findById(id: Uuid): Game? = transaction {
		Game.findById(id)
	}
}
