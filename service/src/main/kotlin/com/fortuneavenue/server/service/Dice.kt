package com.fortuneavenue.server.service

import org.springframework.stereotype.Component

/**
 * Pulled out behind an interface (rather than calling kotlin.random.Random
 * directly) so tests can stub deterministic values instead of asserting
 * against whatever an actual roll happens to produce.
 */
interface Dice {
	/** Rolls one die. */
	fun roll(): Int
}

@Component
class RandomDice : Dice {
	override fun roll(): Int = (1..SIDES).random()

	companion object {
		private const val SIDES = 6
	}
}
