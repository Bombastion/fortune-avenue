package com.fortuneavenue.server.graph

/**
 * Generic directed-graph traversal, independent of what a "node" actually
 * is -- callers supply [start] and a [neighbors] function describing the
 * graph.
 */
object GraphTraversal {

	/**
	 * Returns every node reachable from [start] by following [neighbors].
	 * Each node is expanded at most once: [start] is marked visited before
	 * the walk begins, so arriving back at it (closing a loop) simply ends
	 * that branch rather than re-expanding it, and the same dedup applies
	 * to every other node too -- so no cycle, through start or otherwise,
	 * can cause an infinite loop.
	 */
	fun <T> reachableFrom(
		start: T,
		strategy: TraversalStrategy,
		neighbors: (T) -> List<T>,
	): Set<T> {
		val visited = linkedSetOf(start)
		val pending = ArrayDeque<T>()
		pending.add(start)

		while (pending.isNotEmpty()) {
			val current = when (strategy) {
				TraversalStrategy.BREADTH_FIRST -> pending.removeFirst()
				TraversalStrategy.DEPTH_FIRST -> pending.removeLast()
			}

			for (next in neighbors(current)) {
				if (visited.add(next)) {
					pending.addLast(next)
				}
			}
		}

		return visited
	}
}
