package com.fortuneavenue.server.service

import kotlin.uuid.Uuid

/**
 * A point-in-time snapshot of everything about one game a client would otherwise have to
 * reconstruct by having seen every event live -- see [GameSimulationService.getSnapshot], and
 * GameWebSocketHandler, which sends one of these (as a `game_state` event) right after a client
 * connects, so joining or reconnecting partway through a game doesn't leave it guessing at
 * positions/gold/whatever decision is currently paused.
 */
data class GameSnapshot(
    val turnOrder: List<Uuid>?,
    val turnNumber: Int,
    val gameOver: Boolean,
    /** Null before the game starts, or once it's over. */
    val activePlayerId: Uuid?,
    val pendingDecision: PendingDecisionSnapshot?,
    val players: List<PlayerSnapshot>,
    /** Every SHOP space's current value in this game, owned or not -- lets a reconnecting client
     * show accurate prices for shops it hasn't personally seen a shop_purchased/
     * district_values_recalculated event for yet. */
    val shopValues: List<ShopValueSnapshot>,
    /** Every district's current per-share stock value in this game. */
    val stockValues: List<StockValueSnapshot>,
)

data class PlayerSnapshot(
    val playerId: Uuid,
    val ready: Boolean,
    /** Null only if the game hasn't started yet -- once it has, a player who hasn't moved is still
     * sitting at the board's start space (see PlayerStatesTable.currentSpaceId). */
    val currentSpaceId: Uuid?,
    val currentGold: Int,
    val heldSuits: List<String>,
    val promotionCount: Int,
    val ownedShopSpaceIds: List<Uuid>,
    val stockHoldings: List<StockHoldingSnapshot>,
)

data class StockHoldingSnapshot(val districtId: Uuid, val quantity: Int)

data class ShopValueSnapshot(val spaceId: Uuid, val currentValue: Int)

data class StockValueSnapshot(val districtId: Uuid, val currentStockValue: Int)

/**
 * Whichever decision (if any) [GameSnapshot.activePlayerId] currently has movement paused on --
 * mirrors [GameSimulationService.TurnEvent]'s ChoiceRequired/ShopPurchaseAvailable/
 * StockTradingAvailable one-for-one, just derived by reading back persisted state (see
 * [GameSimulationService.pendingDecisionFor]) instead of live from a move actually happening. At
 * most one of these is ever true at a time -- the same three pauses are mutually exclusive live,
 * too.
 */
sealed interface PendingDecisionSnapshot {
    data class ChoicePending(val spaceId: Uuid, val options: List<GameSimulationService.PathOption>) :
        PendingDecisionSnapshot

    data class ShopPurchasePending(val spaceId: Uuid, val price: Int) : PendingDecisionSnapshot

    data class StockTradePending(val spaceId: Uuid, val offers: List<StockTradeOffer>) :
        PendingDecisionSnapshot
}
