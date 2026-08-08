package com.fortuneavenue.server.graph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class GraphTraversalTest {

	@ParameterizedTest
	@EnumSource(TraversalStrategy::class)
	fun `finds every node in a simple cycle`(strategy: TraversalStrategy) {
		// 0 -> 1 -> 2 -> 0
		val edges = mapOf(0 to listOf(1), 1 to listOf(2), 2 to listOf(0))

		val reachable = GraphTraversal.reachableFrom(0, strategy) { edges[it].orEmpty() }

		assertThat(reachable).containsExactlyInAnyOrder(0, 1, 2)
	}

	@ParameterizedTest
	@EnumSource(TraversalStrategy::class)
	fun `does not include nodes that are not reachable`(strategy: TraversalStrategy) {
		// 0 -> 1, and 2 is disconnected
		val edges = mapOf(0 to listOf(1))

		val reachable = GraphTraversal.reachableFrom(0, strategy) { edges[it].orEmpty() }

		assertThat(reachable).containsExactlyInAnyOrder(0, 1)
	}

	@ParameterizedTest
	@EnumSource(TraversalStrategy::class)
	fun `follows a branch that later rejoins without visiting twice or looping forever`(strategy: TraversalStrategy) {
		// 0 -> 1 -> 3 -> 0
		// 0 -> 2 -> 3
		val edges = mapOf(0 to listOf(1, 2), 1 to listOf(3), 2 to listOf(3), 3 to listOf(0))

		val reachable = GraphTraversal.reachableFrom(0, strategy) { edges[it].orEmpty() }

		assertThat(reachable).containsExactlyInAnyOrder(0, 1, 2, 3)
	}
}
