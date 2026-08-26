// Mirrors service/.../service/BoardGraphValidator.kt: a board is only valid once every space is
// reachable from the start space, and the start space is reachable from every space (so play can
// always loop back around). Both checks are a plain BFS over the path list, once forward and once
// over the reversed edges.

export interface GraphEdge {
  from: number;
  to: number;
}

function reachableFrom(start: number, neighborsOf: Map<number, number[]>): Set<number> {
  const visited = new Set<number>([start]);
  const queue: number[] = [start];

  while (queue.length > 0) {
    const current = queue.shift() as number;
    for (const next of neighborsOf.get(current) ?? []) {
      if (!visited.has(next)) {
        visited.add(next);
        queue.push(next);
      }
    }
  }

  return visited;
}

function groupNeighbors(edges: GraphEdge[], key: "from" | "to", value: "from" | "to") {
  const map = new Map<number, number[]>();
  for (const edge of edges) {
    const list = map.get(edge[key]) ?? [];
    list.push(edge[value]);
    map.set(edge[key], list);
  }
  return map;
}

/**
 * Returns board-graph error messages for [spaceCount] spaces connected by [edges], given a
 * [start] space index. Mirrors BoardGraphValidator.kt's messages closely enough to be recognizable
 * next to the server's own errors.
 */
export function validateBoardGraph(
  spaceCount: number,
  edges: GraphEdge[],
  start: number,
): string[] {
  if (spaceCount === 0) {
    return ["A board needs at least one space."];
  }

  if (start < 0 || start >= spaceCount) {
    return [`Start space index ${start} is out of range for ${spaceCount} spaces.`];
  }

  const outOfRange = edges.filter(
    (edge) => edge.from < 0 || edge.from >= spaceCount || edge.to < 0 || edge.to >= spaceCount,
  );
  if (outOfRange.length > 0) {
    return ["One or more paths reference a space index outside the valid range."];
  }

  const errors: string[] = [];
  const allSpaces = new Set(Array.from({ length: spaceCount }, (_, i) => i));

  const forward = groupNeighbors(edges, "from", "to");
  const reachableForward = reachableFrom(start, forward);
  const unreachable = [...allSpaces].filter((s) => !reachableForward.has(s));
  if (unreachable.length > 0) {
    errors.push(
      `Space(s) at index [${unreachable.join(", ")}] are not reachable from the start space.`,
    );
  }

  const backward = groupNeighbors(edges, "to", "from");
  const canReachStart = reachableFrom(start, backward);
  const deadEnds = [...allSpaces].filter((s) => !canReachStart.has(s));
  if (deadEnds.length > 0) {
    errors.push(
      `Space(s) at index [${deadEnds.join(", ")}] can never make it back to the start space.`,
    );
  }

  return errors;
}
