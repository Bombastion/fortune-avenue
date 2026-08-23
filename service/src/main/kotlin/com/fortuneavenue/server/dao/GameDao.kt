package com.fortuneavenue.server.dao

import com.fortuneavenue.server.models.board.db.BoardSpacesTable
import com.fortuneavenue.server.models.board.db.BoardsTable
import com.fortuneavenue.server.models.game.db.Game
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository

@Repository
class GameDao {

    fun create(boardId: Uuid, targetNetWorth: Int): Game = transaction {
        Game.new {
            this.boardId = EntityID(boardId, BoardsTable)
            this.targetNetWorth = targetNetWorth
        }
    }

    fun findById(id: Uuid): Game? = transaction { Game.findById(id) }

    fun startGame(id: Uuid, turnOrder: List<Uuid>): Game? = transaction {
        Game.findById(id)?.apply { this.turnOrder = turnOrder }
    }

    /**
     * Records a roll's remaining movement (or, once a turn pauses on a branch choice, however much
     * is left of it).
     */
    fun setMovementPoints(id: Uuid, points: Int?): Game? = transaction {
        Game.findById(id)?.apply { currentMovementPoints = points }
    }

    /**
     * Sets (or, with a null [spaceId], clears) the BANK space [id]'s turn is currently paused on
     * for a stock trade decision -- see GamesTable.pendingStockTradeSpaceId and
     * GameSimulationService.checkStockTrade.
     */
    fun setPendingStockTradeSpace(id: Uuid, spaceId: Uuid?): Game? = transaction {
        Game.findById(id)?.apply {
            pendingStockTradeSpaceId = spaceId?.let { EntityID(it, BoardSpacesTable) }
        }
    }

    /**
     * Ends the current player's turn: advances to the next player and clears any leftover movement.
     */
    fun advanceTurn(id: Uuid): Game? = transaction {
        Game.findById(id)?.apply {
            turnNumber += 1
            currentMovementPoints = null
        }
    }

    /**
     * Records the turn number [id] actually ended on
     */
    fun setEndedOnTurn(id: Uuid, turnNumber: Int): Game? = transaction {
        Game.findById(id)?.apply { endedOnTurn = turnNumber }
    }
}
