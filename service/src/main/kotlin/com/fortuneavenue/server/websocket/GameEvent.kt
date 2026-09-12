package com.fortuneavenue.server.websocket

/**
 * Everything the server can send back over the socket. Ids are strings -- see the REST response
 * DTOs for why.
 */
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

/**
 * [playerId] picked up [suit] (one of HEART/DIAMOND/SPADE/CLUB) by passing or landing on [spaceId].
 */
data class SuitPickedUpEvent(
    val playerId: String,
    val spaceId: String,
    val suit: String,
    override val type: String = "suit_picked_up",
) : GameEvent

/**
 * [playerId] was promoted at [spaceId] (a BANK space) after passing or landing on it while holding
 * all 4 suits -- their held suits have been cleared, their promotion count went up by one, and they
 * were paid [goldAwarded] gold.
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
    val movementPointsRemaining: Int,
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

/**
 * Movement ended on [spaceId], a SHOP [playerId] already owns with investable headroom left
 * ([maxCap] > 0) -- paused until [playerId] sends `invest` or `decline_invest` to decide what to
 * do, exactly like [ShopPurchaseAvailableEvent] pauses for an unowned shop. [currentValue] is the
 * shop's value before whatever gets invested.
 */
data class InvestmentAvailableEvent(
    val playerId: String,
    val spaceId: String,
    val currentValue: Int,
    val maxCap: Int,
    override val type: String = "investment_available",
) : GameEvent

/**
 * [playerId] invested [amount] gold into the shop they were paused on at [spaceId], deducted from
 * their gold -- its currentValue is now [newCurrentValue] and its remaining investable headroom is
 * now [newMaxCap]. Followed by a turn_ended, exactly like [ShopPurchasedEvent] is for a purchase.
 */
data class InvestedEvent(
    val playerId: String,
    val spaceId: String,
    val amount: Int,
    val newCurrentValue: Int,
    val newMaxCap: Int,
    override val type: String = "invested",
) : GameEvent

/**
 * [playerId] landed on [spaceId], a SHOP owned by [ownerId], and paid them [amount] gold in toll.
 */
data class TollPaidEvent(
    val playerId: String,
    val spaceId: String,
    val ownerId: String,
    val amount: Int,
    override val type: String = "toll_paid",
) : GameEvent

/**
 * One district's stock a player can buy or sell with a `buy_stock`/`sell_stock` message, naming its
 * `districtId`.
 */
data class StockTradeOfferPayload(
    val districtId: String,
    val pricePerShare: Int,
    val ownedQuantity: Int,
)

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

/**
 * It's [playerId]'s turn and they need to roll -- nothing else is going to announce this for them.
 */
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

data class PlayerSnapshotPayload(
    val playerId: String,
    val ready: Boolean,
    val currentSpaceId: String?,
    val currentGold: Int,
    val heldSuits: List<String>,
    val promotionCount: Int,
    val ownedShopSpaceIds: List<String>,
    val stockQuantitiesByDistrictId: Map<String, Int>,
)

/**
 * Sent once, right after [ConnectedEvent], so a client connecting (or reconnecting) partway through
 * a game doesn't have to have seen every event live to know where things stand -- see
 * GameSimulationService.getSnapshot. [pendingChoiceRequired]/[pendingShopPurchaseAvailable]/
 * [pendingInvestmentAvailable]/[pendingStockTradingAvailable] deliberately reuse those events' own
 * shape (playerId and all) rather than inventing a new one, so a client can fold whichever one is
 * non-null onto its state the exact same way it would the live event that originally caused that
 * pause -- at most one is ever non-null, naming whatever [activePlayerId] currently has movement
 * paused on.
 */
data class GameStateSnapshotEvent(
    val turnOrder: List<String>?,
    val turnNumber: Int,
    val gameOver: Boolean,
    val activePlayerId: String?,
    val pendingChoiceRequired: ChoiceRequiredEvent? = null,
    val pendingShopPurchaseAvailable: ShopPurchaseAvailableEvent? = null,
    val pendingInvestmentAvailable: InvestmentAvailableEvent? = null,
    val pendingStockTradingAvailable: StockTradingAvailableEvent? = null,
    val players: List<PlayerSnapshotPayload>,
    val shopValuesBySpaceId: Map<String, Int>,
    /**
     * Every SHOP space's current investable headroom in this game, owned or not (0 for an unowned
     * one -- see GameShopInformationDao.seedForGame) -- lets a reconnecting client know how much
     * more it can `invest` into a shop it owns without having seen every `shop_purchased`/
     * `invested` event that shaped that number live.
     */
    val shopMaxCapsBySpaceId: Map<String, Int>,
    val stockValuesByDistrictId: Map<String, Int>,
    override val type: String = "game_state",
) : GameEvent
