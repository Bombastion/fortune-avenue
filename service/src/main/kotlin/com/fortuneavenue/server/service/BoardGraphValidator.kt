package com.fortuneavenue.server.service

import com.fortuneavenue.server.graph.GraphTraversal
import com.fortuneavenue.server.graph.TraversalStrategy

/**
 * Validates a proposed board's graph shape before anything is persisted.
 * A board is valid when:
 *  - every space is reachable from the start space, and
 *  - the start space is reachable from every space
 */
object BoardGraphValidator {

	data class Edge(val from: Int, val to: Int)

	fun validate(
		spaceCount: Int,
		edges: List<Edge>,
		start: Int,
		strategy: TraversalStrategy = TraversalStrategy.BREADTH_FIRST,
	): List<String> {
		if (spaceCount == 0) {
			return listOf("A board needs at least one space.")
		}

		if (start !in 0 until spaceCount) {
			return listOf("Start space index $start is out of range for $spaceCount spaces.")
		}

		val outOfRangeEdges = edges.filter { it.from !in 0 until spaceCount || it.to !in 0 until spaceCount }
		if (outOfRangeEdges.isNotEmpty()) {
			return listOf("Path(s) $outOfRangeEdges reference a space index outside the valid range.")
		}

		val errors = mutableListOf<String>()
		val allSpaces = (0 until spaceCount).toSet()

		val forwardNeighbors = edges.groupBy({ it.from }, { it.to })
		val reachableFromStart = GraphTraversal.reachableFrom(start, strategy) { forwardNeighbors[it].orEmpty() }
		val unreachable = allSpaces - reachableFromStart
		if (unreachable.isNotEmpty()) {
			errors += "Space(s) at index $unreachable are not reachable from the start space."
		}

		val backwardNeighbors = edges.groupBy({ it.to }, { it.from })
		val canReachStart = GraphTraversal.reachableFrom(start, strategy) { backwardNeighbors[it].orEmpty() }
		val deadEnds = allSpaces - canReachStart
		if (deadEnds.isNotEmpty()) {
			errors += "Space(s) at index $deadEnds can never make it back to the start space."
		}

		return errors
	}
}
