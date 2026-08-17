package com.fortuneavenue.server.websocket

/**
 * Every message a client sends is `{"type": "..."}`, plus whatever extra fields that type needs --
 * gameId/playerId are already known from the connection. `choose_path` needs a spaceId: `{"type":
 * "choose_path", "spaceId": "<id of the space to move onto>"}`. `buy_shop` and `decline_shop` need
 * nothing extra -- which shop is implied by whichever `shop_purchase_available` event is currently
 * pending. `buy_stock` and `sell_stock` need a districtId and a quantity (1-99): `{"type":
 * "buy_stock", "districtId": "<id>", "quantity": 10}` -- which BANK stop they resolve is implied
 * the same way, by whichever `stock_trading_available` event is currently pending.
 * `skip_stock_trade` needs nothing extra, same as `decline_shop`.
 */
data class ClientMessage(
    val type: String,
    val spaceId: String? = null,
    val districtId: String? = null,
    val quantity: Int? = null,
)

object ClientMessageType {
    const val READY = "ready"
    const val ROLL_DICE = "roll_dice"
    const val CHOOSE_PATH = "choose_path"
    const val BUY_SHOP = "buy_shop"
    const val DECLINE_SHOP = "decline_shop"
    const val BUY_STOCK = "buy_stock"
    const val SELL_STOCK = "sell_stock"
    const val SKIP_STOCK_TRADE = "skip_stock_trade"
}
