package com.fortuneavenue.server.dao

import com.fortuneavenue.server.models.board.db.BoardsTable
import com.fortuneavenue.server.models.game.db.Game
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository
import kotlin.uuid.Uuid

@Repository
class GameDao {

	fun create(boardId: Uuid): Game = transaction {
		Game.new {
			this.boardId = EntityID(boardId, BoardsTable)
		}
	}

	fun findById(id: Uuid): Game? = transaction {
		Game.findById(id)
	}
}
