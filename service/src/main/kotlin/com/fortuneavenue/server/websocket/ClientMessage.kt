package com.fortuneavenue.server.websocket

/**
 * Every message a client sends is `{"type": "..."}`, plus whatever extra
 * fields that type needs -- gameId/playerId are already known from the
 * connection. `choose_path` needs a spaceId:
 * `{"type": "choose_path", "spaceId": "<id of the space to move onto>"}`.
 * `buy_shop` and `decline_shop` need nothing extra -- which shop is implied
 * by whichever `shop_purchase_available` event is currently pending.
 */
data class ClientMessage(
	val type: String,
	val spaceId: String? = null,
)

object ClientMessageType {
	const val READY = "ready"
	const val ROLL_DICE = "roll_dice"
	const val CHOOSE_PATH = "choose_path"
	const val BUY_SHOP = "buy_shop"
	const val DECLINE_SHOP = "decline_shop"
}
