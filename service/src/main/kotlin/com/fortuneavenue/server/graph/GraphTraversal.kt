package com.fortuneavenue.server.graph

/** Generic directed-graph traversal */
object GraphTraversal {

    /**
     * Returns every node reachable from [start] by following [neighbors]. Each node is expanded at
     * most once
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
            val current =
                when (strategy) {
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
