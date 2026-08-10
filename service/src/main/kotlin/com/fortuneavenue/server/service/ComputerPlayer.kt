package com.fortuneavenue.server.service

import org.springframework.stereotype.Component

/**
 * Encapsulates how a computer opponent behaves on its own turn. Right now
 * that's just picking a path at a branch (randomly), but this is meant to
 * be where other kinds of computer decisions land too as the game grows
 * (buying properties, choosing what to do with a card draw, etc.) rather
 * than having "what a computer does" spread across GameSimulationService.
 */
interface ComputerPlayer {
	/** Picks which of a branch's outgoing paths to take. */
	fun <T> chooseBranch(options: List<T>): T
}

@Component
class RandomComputerPlayer : ComputerPlayer {
	override fun <T> chooseBranch(options: List<T>): T = options.random()
}
