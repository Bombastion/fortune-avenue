package com.fortuneavenue.server.websocket

/** Everything the server can send back over the socket. Ids are strings -- see the REST response DTOs for why. */
sealed interface GameEvent {
	val type: String
}

data class ConnectedEvent(
	val playerId: String,
	override val type: String = "connected",
) : GameEvent

data class PlayerReadyEvent(
	val playerId: String,
	override val type: String = "player_ready",
) : GameEvent

data class GameStartedEvent(
	val turnOrder: List<String>,
	override val type: String = "game_started",
) : GameEvent

data class DiceRolledEvent(
	val playerId: String,
	val roll: Int,
	override val type: String = "dice_rolled",
) : GameEvent

data class PlayerMovedEvent(
	val turnNumber: Int,
	val playerId: String,
	val fromSpaceId: String,
	val toSpaceId: String,
	val movementPointsRemaining: Int,
	override val type: String = "player_moved",
) : GameEvent

/** [playerId] picked up [suit] (one of HEART/DIAMOND/SPADE/CLUB) by passing or landing on [spaceId]. */
data class SuitPickedUpEvent(
	val playerId: String,
	val spaceId: String,
	val suit: String,
	override val type: String = "suit_picked_up",
) : GameEvent

/**
 * [playerId] was promoted at [spaceId] (a BANK space) after passing or landing on it while
 * holding all 4 suits -- their held suits have been cleared, their promotion count went up by
 * one, and they were paid [goldAwarded] gold.
 */
data class PromotedEvent(
	val playerId: String,
	val spaceId: String,
	val goldAwarded: Int,
	override val type: String = "promoted",
) : GameEvent

/** One outgoing path a player can pick with a `choose_path` message. */
data class PathOptionPayload(val toSpaceId: String, val branchOrder: Int)

data class ChoiceRequiredEvent(
	val playerId: String,
	val spaceId: String,
	val options: List<PathOptionPayload>,
	override val type: String = "choice_required",
) : GameEvent

data class ShopPurchaseAvailableEvent(
	val playerId: String,
	val spaceId: String,
	val price: Int,
	override val type: String = "shop_purchase_available",
) : GameEvent

data class ShopPurchasedEvent(
	val playerId: String,
	val spaceId: String,
	val price: Int,
	override val type: String = "shop_purchased",
) : GameEvent

/** [newValuesBySpaceId] maps each recalculated shop's spaceId to its new currentValue. */
data class DistrictValuesRecalculatedEvent(
	val playerId: String,
	val districtId: String,
	val newValuesBySpaceId: Map<String, Int>,
	override val type: String = "district_values_recalculated",
) : GameEvent

/** One district's stock a player can buy or sell with a `buy_stock`/`sell_stock` message, naming its `districtId`. */
data class StockTradeOfferPayload(val districtId: String, val pricePerShare: Int, val ownedQuantity: Int)

data class StockTradingAvailableEvent(
	val playerId: String,
	val spaceId: String,
	val offers: List<StockTradeOfferPayload>,
	override val type: String = "stock_trading_available",
) : GameEvent

data class StockPurchasedEvent(
	val playerId: String,
	val districtId: String,
	val quantity: Int,
	val pricePerShare: Int,
	val totalCost: Int,
	override val type: String = "stock_purchased",
) : GameEvent

data class StockSoldEvent(
	val playerId: String,
	val districtId: String,
	val quantity: Int,
	val pricePerShare: Int,
	val totalProceeds: Int,
	override val type: String = "stock_sold",
) : GameEvent

data class TurnEndedEvent(
	val turnNumber: Int,
	val playerId: String,
	override val type: String = "turn_ended",
) : GameEvent

/** It's [playerId]'s turn and they need to roll -- nothing else is going to announce this for them. */
data class TurnStartedEvent(
	val playerId: String,
	val turnNumber: Int,
	override val type: String = "turn_started",
) : GameEvent

data class GameOverEvent(
	val turnCount: Int,
	override val type: String = "game_over",
) : GameEvent

data class ErrorEvent(
	val message: String,
	override val type: String = "error",
) : GameEvent
