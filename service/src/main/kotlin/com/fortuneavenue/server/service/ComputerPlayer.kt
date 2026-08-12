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
	 * Decides whether to buy an unowned shop the computer player just landed on, given
	 * [currentGold] -- how much gold it actually has on hand right now. Affordability is this
	 * decision's responsibility entirely: GameSimulationService doesn't second-guess it, so an
	 * implementation that says yes to a shop it can't afford would genuinely go through (and
	 * leave currentGold negative, same as it would for a human -- see PlayerStatesTable.currentGold).
	 */
	fun shouldBuyShop(shop: GameShopInformation, currentGold: Int): Boolean
}

@Component
class RandomComputerPlayer : ComputerPlayer {
	override fun <T> chooseBranch(options: List<T>): T = options.random()

	// Simplest possible policy for now -- buy if (and only if) it can actually afford the price.
	override fun shouldBuyShop(shop: GameShopInformation, currentGold: Int): Boolean = currentGold >= shop.currentValue
}
