package com.fortuneavenue.server.service

import com.fortuneavenue.server.models.board.db.GameShopInformation
import org.springframework.stereotype.Component

/**
 * Encapsulates how a computer opponent behaves on its own turn. Right now
 * that's picking a path at a branch (randomly) and deciding whether to buy
 * an unowned shop it lands on (based on whether it can afford it), but this
 * is meant to be where other kinds of computer decisions land too as the
 * game grows (choosing what to do with a card draw, etc.) rather than
 * having "what a computer does" spread across GameSimulationService.
 */
interface ComputerPlayer {
	/** Picks which of a branch's outgoing paths to take. */
	fun <T> chooseBranch(options: List<T>): T

	/**
	 * Decides whether the computer player *wants* to buy an unowned shop it just landed on, given
	 * [currentGold] -- how much gold it actually has on hand right now. This isn't the only word
	 * on whether the purchase actually happens, though: GameSimulationService still enforces that
	 * a purchase can't leave currentGold negative (same floor a human's buyShop is held to -- see
	 * PlayerStatesTable.currentGold for why that's fine *after* a purchase, just not to start
	 * one), so saying yes here to a shop it can't afford is simply treated the same as saying no.
	 */
	fun shouldBuyShop(shop: GameShopInformation, currentGold: Int): Boolean
}

@Component
class RandomComputerPlayer : ComputerPlayer {
	override fun <T> chooseBranch(options: List<T>): T = options.random()

	// Simplest possible policy for now -- buy if (and only if) it can actually afford the price.
	override fun shouldBuyShop(shop: GameShopInformation, currentGold: Int): Boolean = currentGold >= shop.currentValue
}
