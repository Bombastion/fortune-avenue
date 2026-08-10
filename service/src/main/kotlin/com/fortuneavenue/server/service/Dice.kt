package com.fortuneavenue.server.service

import org.springframework.stereotype.Component

/**
 * Randomness seam for turns: rolling a die and, for computer players,
 * picking a branch when a space has more than one outgoing path. Pulled out
 * behind an interface (rather than calling kotlin.random.Random directly)
 * so tests can stub deterministic values instead of asserting against
 * whatever an actual roll/shuffle happens to produce.
 */
interface Dice {
	/** Rolls one die. */
	fun roll(): Int

	/** A computer player's pick among a branch's outgoing paths. */
	fun <T> choose(options: List<T>): T
}

@Component
class RandomDice : Dice {
	override fun roll(): Int = (1..SIDES).random()

	override fun <T> choose(options: List<T>): T = options.random()

	companion object {
		private const val SIDES = 6
	}
}
