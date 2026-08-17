package com.fortuneavenue.server.service

import com.fortuneavenue.server.models.board.db.GameShopInformation
import kotlin.uuid.Uuid
import org.springframework.stereotype.Component

/**
 * One district's stock a player could buy or sell, offered by a
 * [GameSimulationService.TurnEvent.StockTradingAvailable] pause -- or to
 * [ComputerPlayer.chooseStockTrade] for a computer player deciding immediately instead.
 * [ownedQuantity] is however many shares the deciding player already holds in this district.
 */
data class StockTradeOffer(
    val districtId: Uuid,
    val gameDistrictInformationId: Uuid,
    val pricePerShare: Int,
    val ownedQuantity: Int,
)

/**
 * What a computer player decided to do with one of the [StockTradeOffer]s it was given -- see
 * [ComputerPlayer.chooseStockTrade].
 */
sealed interface StockTradeDecision {
    data class Buy(val districtId: Uuid, val quantity: Int) : StockTradeDecision

    data class Sell(val districtId: Uuid, val quantity: Int) : StockTradeDecision
}

/**
 * Encapsulates how a computer opponent behaves on its own turn. Right now that's picking a path at
 * a branch (randomly), deciding whether to buy an unowned shop it lands on (based on whether it can
 * afford it), and deciding whether to trade stock at a BANK space it passes or lands on (no real
 * policy yet -- see [chooseStockTrade]), but this is meant to be where other kinds of computer
 * decisions land too as the game grows (choosing what to do with a card draw, etc.) rather than
 * having "what a computer does" spread across GameSimulationService.
 */
interface ComputerPlayer {
    /** Picks which of a branch's outgoing paths to take. */
    fun <T> chooseBranch(options: List<T>): T

    /**
     * Decides whether the computer player *wants* to buy an unowned shop it just landed on, given
     * [currentGold] -- how much gold it actually has on hand right now. This isn't the only word on
     * whether the purchase actually happens, though: GameSimulationService still enforces that a
     * purchase can't leave currentGold negative (same floor a human's buyShop is held to -- see
     * PlayerStatesTable.currentGold for why that's fine *after* a purchase, just not to start one),
     * so saying yes here to a shop it can't afford is simply treated the same as saying no.
     */
    fun shouldBuyShop(shop: GameShopInformation, currentGold: Int): Boolean

    /**
     * Decides whether the computer player wants to buy or sell stock at a BANK space it just passed
     * or landed on, given [offers] (one per district with stock available to trade -- see
     * GameDistrictInformationDao) and [currentGold]. GameSimulationService enforces the same floors
     * a human is held to regardless of what this says (can't buy more than currentGold covers,
     * can't sell more than currently held, quantity between 1 and 99), exactly like
     * [shouldBuyShop].
     */
    fun chooseStockTrade(offers: List<StockTradeOffer>, currentGold: Int): StockTradeDecision?
}

@Component
class RandomComputerPlayer : ComputerPlayer {
    override fun <T> chooseBranch(options: List<T>): T = options.random()

    // Simplest possible policy for now -- buy if (and only if) it can actually afford the price.
    override fun shouldBuyShop(shop: GameShopInformation, currentGold: Int): Boolean =
        currentGold >= shop.currentValue

    // No stock trading policy implemented yet -- always skips.
    override fun chooseStockTrade(
        offers: List<StockTradeOffer>,
        currentGold: Int,
    ): StockTradeDecision? = null
}
