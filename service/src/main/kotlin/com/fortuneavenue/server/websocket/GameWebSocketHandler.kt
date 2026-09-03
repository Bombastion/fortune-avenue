package com.fortuneavenue.server.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.fortuneavenue.server.dao.PlayerDao
import com.fortuneavenue.server.service.GameSimulationService
import com.fortuneavenue.server.service.GameSnapshot
import com.fortuneavenue.server.service.PendingDecisionSnapshot
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import org.springframework.web.socket.handler.TextWebSocketHandler
import org.springframework.web.util.UriComponentsBuilder

private data class Connection(val gameId: Uuid, val playerId: Uuid)

/**
 * Connect as `/ws/game?gameId=<id>&playerId=<id>`. The connection is closed immediately if either
 * id is missing/malformed, or playerId doesn't refer to a real player in that game.
 *
 * Once connected, a client can send `{"type":"ready"}` to mark itself ready (once every human
 * player in the game has, computer players are readied up automatically and turn order is randomly
 * decided as the game starts -- if that puts one or more computer players first, their turns get
 * played out and broadcast immediately, right after the game_started event) and
 * `{"type":"roll_dice"}` on its turn to roll and move forward that many spaces. Every space a
 * player passes or lands on along the way is checked for a `suit_picked_up` event -- landing on or
 * passing a HEART/DIAMOND/ SPADE/CLUB space picks that suit up the first time, broadcast right
 * after the `player_moved` event for that space (nothing is broadcast for a suit already held). The
 * same space is also checked for a `promoted` event -- passing or landing on a BANK space while
 * currently holding all 4 suits clears them all, bumps a promotion count, and pays out gold (named
 * in the event as `goldAwarded`), broadcast right after that space's `player_moved` event (nothing
 * is broadcast for a BANK space visited without every suit in hand). Independent of promotion, that
 * same BANK space is also checked for a `stock_trading_available` event -- if any district has
 * stock to trade in this game, movement pauses there (even mid-move, not just when it's the final
 * stop) with an event naming every tradeable district's `districtId`, `pricePerShare`, and however
 * much of it the player already owns -- respond with
 * `{"type":"buy_stock","districtId":"<id>","quantity":10}`,
 * `{"type":"sell_stock","districtId":"<id>","quantity":10}` (quantity is 1-99 either way), or
 * `{"type":"skip_stock_trade"}` to resolve it and keep moving; `buy_stock`/`sell_stock` come back
 * as an `error` instead if the trade isn't affordable/owned, leaving the decision still pending.
 * Buying or selling broadcasts `stock_purchased`/`stock_sold`. If that movement reaches a space
 * with more than one path out of it, it pauses there and a `choice_required` event lists the
 * options -- respond with `{"type":"choose_path","spaceId":"<id>"}` to pick one and keep moving. If
 * movement instead runs out on an unowned shop, it pauses there too with a
 * `shop_purchase_available` event naming the price -- respond with `{"type":"buy_shop"}` or
 * `{"type":"decline_shop"}` to decide -- `buy_shop` comes back as an `error` instead if the player
 * can't afford the price, leaving the decision still pending. Buying broadcasts `shop_purchased`,
 * followed by `district_values_recalculated` if it brought the buyer's owned count in that shop's
 * district to 2 or more. Any computer players whose turns immediately follow (once the current
 * player's turn actually ends) are played out automatically too, each broadcast in turn order right
 * after the requested one -- including their own shop purchase and stock trade decisions, made
 * immediately rather than paused on. The moment that chain lands on a human player, a
 * `turn_started` event names them -- computer turns don't get one, since their own
 * dice_rolled/player_moved events already make it obvious whose turn it was. See
 * GameSimulationService for the actual rules.
 *
 * Session bookkeeping (who's connected to which game) lives in memory on this instance -- unlike
 * the rest of this class, which just delegates to PlayerDao/GameSimulationService, this part
 * genuinely only works for a single server instance: broadcasting an event only reaches sessions
 * connected to *this* instance. Fine for a small simulation; would need something like Postgres
 * LISTEN/NOTIFY or Redis pub/sub to work across replicas.
 */
@Component
class GameWebSocketHandler(
    private val playerDao: PlayerDao,
    private val gameSimulationService: GameSimulationService,
    private val objectMapper: ObjectMapper,
) : TextWebSocketHandler() {

    private val connectionsBySession = ConcurrentHashMap<WebSocketSession, Connection>()
    private val sessionsByGame = ConcurrentHashMap<Uuid, MutableSet<WebSocketSession>>()

    // Raw session -> a decorator that serializes sendMessage calls on it. Needed because a
    // session's turn-ending broadcast and another connection's own error reply (see
    // handleBuyShop and friends) can land on the same session from two different request
    // threads at once when two tabs race each other -- WebSocketSession.sendMessage isn't
    // safe for concurrent calls, and an unguarded collision here previously surfaced as later
    // events (including turn_started) silently never reaching a connection.
    private val concurrentSessions = ConcurrentHashMap<WebSocketSession, WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val params =
            UriComponentsBuilder.fromUri(
                    session.uri ?: return session.reject("Missing connection URI.")
                )
                .build()
                .queryParams
        val gameId = params.getFirst("gameId")?.let { Uuid.parseOrNull(it) }
        val playerId = params.getFirst("playerId")?.let { Uuid.parseOrNull(it) }
        if (gameId == null || playerId == null) {
            return session.reject(
                "gameId and playerId query params are required and must be valid ids."
            )
        }

        val player = playerDao.findById(playerId)
        if (player == null || player.gameId.value != gameId) {
            return session.reject("$playerId is not a player in game $gameId.")
        }

        connectionsBySession[session] = Connection(gameId, playerId)
        concurrentSessions[session] =
            ConcurrentWebSocketSessionDecorator(
                session,
                SEND_TIME_LIMIT_MS,
                SEND_BUFFER_SIZE_BYTES,
            )
        sessionsByGame
            .computeIfAbsent(gameId) { Collections.newSetFromMap(ConcurrentHashMap()) }
            .add(session)

        send(session, ConnectedEvent(playerId = playerId.toString()))

        // Hydrates a client connecting (or reconnecting) partway through a game -- without this
        // it would only know what's happened from events broadcast from this point on. Failure
        // here shouldn't be possible (game/playerId were just validated above) but isn't worth
        // tearing the connection down over either way -- worst case the client just doesn't get
        // a snapshot and relies on live events only, same as before this existed.
        gameSimulationService.getSnapshot(gameId, playerId).onSuccess { snapshot ->
            send(session, snapshot.toWireEvent())
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val connection = connectionsBySession.remove(session) ?: return
        sessionsByGame[connection.gameId]?.remove(session)
        concurrentSessions.remove(session)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val connection = connectionsBySession[session] ?: return
        val clientMessage = runCatching {
            objectMapper.readValue(message.payload, ClientMessage::class.java)
        }
            .getOrNull()

        when (clientMessage?.type) {
            ClientMessageType.READY -> handleReady(session, connection)
            ClientMessageType.ROLL_DICE -> handleRollDice(session, connection)
            ClientMessageType.CHOOSE_PATH ->
                handleChoosePath(session, connection, clientMessage.spaceId)
            ClientMessageType.BUY_SHOP -> handleBuyShop(session, connection)
            ClientMessageType.DECLINE_SHOP -> handleDeclineShop(session, connection)
            ClientMessageType.BUY_STOCK ->
                handleBuyStock(
                    session,
                    connection,
                    clientMessage.districtId,
                    clientMessage.quantity,
                )
            ClientMessageType.SELL_STOCK ->
                handleSellStock(
                    session,
                    connection,
                    clientMessage.districtId,
                    clientMessage.quantity,
                )
            ClientMessageType.SKIP_STOCK_TRADE -> handleSkipStockTrade(session, connection)
            else -> send(session, ErrorEvent("Unrecognized message: ${message.payload}"))
        }
    }

    private fun handleReady(session: WebSocketSession, connection: Connection) {
        gameSimulationService
            .markReady(connection.gameId, connection.playerId)
            .fold(
                onSuccess = { outcome ->
                    broadcast(
                        connection.gameId,
                        PlayerReadyEvent(playerId = connection.playerId.toString()),
                    )
                    if (outcome is GameSimulationService.ReadyOutcome.GameStarted) {
                        broadcast(
                            connection.gameId,
                            GameStartedEvent(turnOrder = outcome.turnOrder.map { it.toString() }),
                        )
                        // If turn order came out with one or more computer players
                        // first, their turns were already played -- broadcast those
                        // too so clients see the board update without anyone having
                        // to roll the dice on their behalf.
                        outcome.openingTurnEvents.forEach { event ->
                            broadcastTurnEvent(connection.gameId, event)
                        }
                    }
                },
                onFailure = { error ->
                    send(session, ErrorEvent(error.message ?: "Unable to mark ready."))
                },
            )
    }

    private fun handleRollDice(session: WebSocketSession, connection: Connection) {
        gameSimulationService
            .rollDice(connection.gameId, connection.playerId)
            .fold(
                onSuccess = { events ->
                    // [events] is the roll, any moves and/or choice pause it
                    // caused, and then any computer players' full turns that got
                    // auto-played right after -- each gets broadcast in order,
                    // same as if every one of them had been requested individually.
                    events.forEach { event -> broadcastTurnEvent(connection.gameId, event) }
                },
                onFailure = { error ->
                    send(session, ErrorEvent(error.message ?: "Unable to roll the dice."))
                },
            )
    }

    private fun handleChoosePath(
        session: WebSocketSession,
        connection: Connection,
        spaceId: String?,
    ) {
        val toSpaceId =
            spaceId?.let { Uuid.parseOrNull(it) }
                ?: return send(session, ErrorEvent("choose_path requires a valid spaceId."))

        gameSimulationService
            .choosePath(connection.gameId, connection.playerId, toSpaceId)
            .fold(
                onSuccess = { events ->
                    events.forEach { event -> broadcastTurnEvent(connection.gameId, event) }
                },
                onFailure = { error ->
                    send(session, ErrorEvent(error.message ?: "Unable to choose a path."))
                },
            )
    }

    private fun handleBuyShop(session: WebSocketSession, connection: Connection) {
        gameSimulationService
            .buyShop(connection.gameId, connection.playerId)
            .fold(
                onSuccess = { events ->
                    events.forEach { event -> broadcastTurnEvent(connection.gameId, event) }
                },
                onFailure = { error ->
                    send(session, ErrorEvent(error.message ?: "Unable to buy the shop."))
                },
            )
    }

    private fun handleDeclineShop(session: WebSocketSession, connection: Connection) {
        gameSimulationService
            .declineShopPurchase(connection.gameId, connection.playerId)
            .fold(
                onSuccess = { events ->
                    events.forEach { event -> broadcastTurnEvent(connection.gameId, event) }
                },
                onFailure = { error ->
                    send(
                        session,
                        ErrorEvent(error.message ?: "Unable to decline the shop purchase."),
                    )
                },
            )
    }

    private fun handleBuyStock(
        session: WebSocketSession,
        connection: Connection,
        districtId: String?,
        quantity: Int?,
    ) {
        val parsedDistrictId =
            districtId?.let { Uuid.parseOrNull(it) }
                ?: return send(session, ErrorEvent("buy_stock requires a valid districtId."))
        val parsedQuantity =
            quantity ?: return send(session, ErrorEvent("buy_stock requires a quantity."))

        gameSimulationService
            .buyStock(connection.gameId, connection.playerId, parsedDistrictId, parsedQuantity)
            .fold(
                onSuccess = { events ->
                    events.forEach { event -> broadcastTurnEvent(connection.gameId, event) }
                },
                onFailure = { error ->
                    send(session, ErrorEvent(error.message ?: "Unable to buy stock."))
                },
            )
    }

    private fun handleSellStock(
        session: WebSocketSession,
        connection: Connection,
        districtId: String?,
        quantity: Int?,
    ) {
        val parsedDistrictId =
            districtId?.let { Uuid.parseOrNull(it) }
                ?: return send(session, ErrorEvent("sell_stock requires a valid districtId."))
        val parsedQuantity =
            quantity ?: return send(session, ErrorEvent("sell_stock requires a quantity."))

        gameSimulationService
            .sellStock(connection.gameId, connection.playerId, parsedDistrictId, parsedQuantity)
            .fold(
                onSuccess = { events ->
                    events.forEach { event -> broadcastTurnEvent(connection.gameId, event) }
                },
                onFailure = { error ->
                    send(session, ErrorEvent(error.message ?: "Unable to sell stock."))
                },
            )
    }

    private fun handleSkipStockTrade(session: WebSocketSession, connection: Connection) {
        gameSimulationService
            .skipStockTrade(connection.gameId, connection.playerId)
            .fold(
                onSuccess = { events ->
                    events.forEach { event -> broadcastTurnEvent(connection.gameId, event) }
                },
                onFailure = { error ->
                    send(session, ErrorEvent(error.message ?: "Unable to skip the stock trade."))
                },
            )
    }

    private fun broadcastTurnEvent(gameId: Uuid, event: GameSimulationService.TurnEvent) {
        broadcast(gameId, event.toWireEvent())
        if (event is GameSimulationService.TurnEvent.TurnEnded && event.gameOver) {
            broadcast(gameId, GameOverEvent(turnCount = event.turnNumber + 1))
        }
    }

    private fun GameSnapshot.toWireEvent(): GameStateSnapshotEvent {
        val activePlayer = activePlayerId?.toString()

        return GameStateSnapshotEvent(
            turnOrder = turnOrder?.map { it.toString() },
            turnNumber = turnNumber,
            gameOver = gameOver,
            activePlayerId = activePlayer,
            pendingChoiceRequired =
                (pendingDecision as? PendingDecisionSnapshot.ChoicePending)?.let {
                    ChoiceRequiredEvent(
                        playerId = activePlayer!!,
                        spaceId = it.spaceId.toString(),
                        options =
                            it.options.map { option ->
                                PathOptionPayload(option.toSpaceId.toString(), option.branchOrder)
                            },
                        movementPointsRemaining = it.movementPointsRemaining,
                    )
                },
            pendingShopPurchaseAvailable =
                (pendingDecision as? PendingDecisionSnapshot.ShopPurchasePending)?.let {
                    ShopPurchaseAvailableEvent(
                        playerId = activePlayer!!,
                        spaceId = it.spaceId.toString(),
                        price = it.price,
                    )
                },
            pendingStockTradingAvailable =
                (pendingDecision as? PendingDecisionSnapshot.StockTradePending)?.let {
                    StockTradingAvailableEvent(
                        playerId = activePlayer!!,
                        spaceId = it.spaceId.toString(),
                        offers =
                            it.offers.map { offer ->
                                StockTradeOfferPayload(
                                    offer.districtId.toString(),
                                    offer.pricePerShare,
                                    offer.ownedQuantity,
                                )
                            },
                    )
                },
            players =
                players.map { p ->
                    PlayerSnapshotPayload(
                        playerId = p.playerId.toString(),
                        ready = p.ready,
                        currentSpaceId = p.currentSpaceId?.toString(),
                        currentGold = p.currentGold,
                        heldSuits = p.heldSuits,
                        promotionCount = p.promotionCount,
                        ownedShopSpaceIds = p.ownedShopSpaceIds.map { it.toString() },
                        stockQuantitiesByDistrictId =
                            p.stockHoldings.associate { it.districtId.toString() to it.quantity },
                    )
                },
            shopValuesBySpaceId = shopValues.associate { it.spaceId.toString() to it.currentValue },
            stockValuesByDistrictId =
                stockValues.associate { it.districtId.toString() to it.currentStockValue },
        )
    }

    private fun GameSimulationService.TurnEvent.toWireEvent(): GameEvent =
        when (this) {
            is GameSimulationService.TurnEvent.DiceRolled ->
                DiceRolledEvent(playerId = playerId.toString(), roll = roll)
            is GameSimulationService.TurnEvent.Moved ->
                PlayerMovedEvent(
                    turnNumber = turnNumber,
                    playerId = playerId.toString(),
                    fromSpaceId = fromSpaceId.toString(),
                    toSpaceId = toSpaceId.toString(),
                    movementPointsRemaining = movementPointsRemaining,
                )
            is GameSimulationService.TurnEvent.SuitPickedUp ->
                SuitPickedUpEvent(
                    playerId = playerId.toString(),
                    spaceId = spaceId.toString(),
                    suit = suit.name,
                )
            is GameSimulationService.TurnEvent.Promoted ->
                PromotedEvent(
                    playerId = playerId.toString(),
                    spaceId = spaceId.toString(),
                    goldAwarded = goldAwarded,
                )
            is GameSimulationService.TurnEvent.ChoiceRequired ->
                ChoiceRequiredEvent(
                    playerId = playerId.toString(),
                    spaceId = spaceId.toString(),
                    options =
                        options.map { PathOptionPayload(it.toSpaceId.toString(), it.branchOrder) },
                    movementPointsRemaining = movementPointsRemaining,
                )
            is GameSimulationService.TurnEvent.ShopPurchaseAvailable ->
                ShopPurchaseAvailableEvent(
                    playerId = playerId.toString(),
                    spaceId = spaceId.toString(),
                    price = price,
                )
            is GameSimulationService.TurnEvent.ShopPurchased ->
                ShopPurchasedEvent(
                    playerId = playerId.toString(),
                    spaceId = spaceId.toString(),
                    price = price,
                )
            is GameSimulationService.TurnEvent.DistrictValuesRecalculated ->
                DistrictValuesRecalculatedEvent(
                    playerId = playerId.toString(),
                    districtId = districtId.toString(),
                    newValuesBySpaceId = newValuesBySpaceId.mapKeys { it.key.toString() },
                )
            is GameSimulationService.TurnEvent.StockTradingAvailable ->
                StockTradingAvailableEvent(
                    playerId = playerId.toString(),
                    spaceId = spaceId.toString(),
                    offers =
                        offers.map {
                            StockTradeOfferPayload(
                                it.districtId.toString(),
                                it.pricePerShare,
                                it.ownedQuantity,
                            )
                        },
                )
            is GameSimulationService.TurnEvent.StockPurchased ->
                StockPurchasedEvent(
                    playerId = playerId.toString(),
                    districtId = districtId.toString(),
                    quantity = quantity,
                    pricePerShare = pricePerShare,
                    totalCost = totalCost,
                )
            is GameSimulationService.TurnEvent.StockSold ->
                StockSoldEvent(
                    playerId = playerId.toString(),
                    districtId = districtId.toString(),
                    quantity = quantity,
                    pricePerShare = pricePerShare,
                    totalProceeds = totalProceeds,
                )
            is GameSimulationService.TurnEvent.TurnEnded ->
                TurnEndedEvent(turnNumber = turnNumber, playerId = playerId.toString())
            is GameSimulationService.TurnEvent.TurnStarted ->
                TurnStartedEvent(playerId = playerId.toString(), turnNumber = turnNumber)
        }

    private fun broadcast(gameId: Uuid, event: GameEvent) {
        sessionsByGame[gameId].orEmpty().forEach { session -> runCatching { send(session, event) } }
    }

    private fun send(session: WebSocketSession, event: GameEvent) {
        if (!session.isOpen) return
        val target = concurrentSessions[session] ?: session
        target.sendMessage(TextMessage(objectMapper.writeValueAsString(event)))
    }

    private fun WebSocketSession.reject(reason: String) {
        close(CloseStatus.BAD_DATA.withReason(reason.take(MAX_CLOSE_REASON_LENGTH)))
    }

    companion object {
        // WebSocket close reasons are limited to 123 UTF-8 bytes by the spec.
        private const val MAX_CLOSE_REASON_LENGTH = 100

        // How long a send may block waiting for a slow/stalled client before
        // ConcurrentWebSocketSessionDecorator gives up and closes the session, and how much
        // it'll buffer for that client in the meantime.
        private const val SEND_TIME_LIMIT_MS = 10_000
        private const val SEND_BUFFER_SIZE_BYTES = 512 * 1024
    }
}
