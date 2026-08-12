package com.fortuneavenue.server.service

import com.fortuneavenue.server.models.board.db.GameShopInformation
import org.springframework.stereotype.Component

/**
 * Encapsulates how a computer opponent behaves on its own turn. Right now
 * that's picking a path at a branch (randomly) and deciding whether to buy
 * an unowned shop it lands on (always), but this is meant to be where other
 * kinds of computer decisions land too as the game grows (choosing what to
 * do with a card draw, etc.) rather than having "what a computer does"
 * spread across GameSimulationService.
 */
interface ComputerPlayer {
	/** Picks which of a branch's outgoing paths to take. */
	fun <T> chooseBranch(options: List<T>): T

	/** Decides whether to buy an unowned shop the computer player just landed on. */
	fun shouldBuyShop(shop: GameShopInformation): Boolean
}

@Component
class RandomComputerPlayer : ComputerPlayer {
	override fun <T> chooseBranch(options: List<T>): T = options.random()

	// Simplest possible policy for now -- always buy. Gold going negative is allowed (see
	// PlayerStatesTable.currentGold), so there's no "can't afford it" case to weigh yet.
	override fun shouldBuyShop(shop: GameShopInformation): Boolean = true
}
