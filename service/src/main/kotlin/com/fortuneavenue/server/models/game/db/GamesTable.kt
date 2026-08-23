package com.fortuneavenue.server.models.game.db

import com.fortuneavenue.server.models.board.db.BoardSpacesTable
import com.fortuneavenue.server.models.board.db.BoardsTable
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.UuidColumnType
import org.jetbrains.exposed.v1.core.dao.id.UuidTable

// Otherwise minimal for now -- players and a board to reference, plus turn
// number. Whatever else a game actually needs (status, ...) lands here once
// that's built.
object GamesTable : UuidTable("games") {
    val boardId = reference("board_id", BoardsTable)

    // Defaults to 0, indicated pre-game
    val turnNumber = integer("turn_number").default(0)

    // Null until every player is READY, at which point it's decided once and
    // fixed for the rest of the game. No FK on array elements (Postgres
    // doesn't support that) -- see the migration.
    val turnOrder = array<Uuid>("turn_order", UuidColumnType()).nullable()

    val maxTurns = integer("max_turns").default(10)

    // Null whenever nobody's mid-turn -- set to the remaining movement from a
    // die roll once the current player rolls, decremented as they move
    // (paused, unchanged, while a human is choosing a branch), and cleared
    // back to null the moment their turn ends.
    val currentMovementPoints = integer("current_movement_points").nullable()

    // Set the moment movement passes or lands on a BANK space (mid-move or as the final stop),
    // pausing there for a stock trade decision -- see GameSimulationService.checkStockTrade.
    // Null the rest of the time. Kept separate from current_movement_points above because this
    // pause can happen with movement still left, unlike a shop purchase (current_movement_points
    // == 0 specifically) -- see the migration.
    val pendingStockTradeSpaceId = optReference("pending_stock_trade_space_id", BoardSpacesTable)

    // Ends the game the moment any player's net worth -- every shop they own plus the current
    // value of every stock they hold, gold on hand excluded (see
    // GameSimulationService.netWorth) -- reaches or exceeds this, exactly as turn_number
    // reaching max_turns already does (see GameSimulationService.endGameIfNetWorthReached).
    // Configurable per game at creation time (see CreateGameRequest) -- no DB-level default;
    // GameService.createGame supplies 6000 when none is given, so the default lives in exactly
    // one place.
    val targetNetWorth = integer("target_net_worth")

    // Records the turn number the game actually ended on -- either turn_number naturally
    // reaching max_turns, or (see GameSimulationService.endGameIfNetWorthReached) a player's net
    // worth reaching target_net_worth first. Null the whole time a game is still in progress.
    // Deciding whether/when a game has ended, and recording this, is the service layer's job, not
    // this table's or GameDao's -- see GameSimulationService.isGameOver and
    // GameDao.setEndedOnTurn.
    val endedOnTurn = integer("ended_on_turn").nullable()
}
