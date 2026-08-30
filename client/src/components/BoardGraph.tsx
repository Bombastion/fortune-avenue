import { useMemo } from "react";
import {
  Background,
  BaseEdge,
  Controls,
  EdgeLabelRenderer,
  Handle,
  MarkerType,
  Position,
  ReactFlow,
  type Edge,
  type EdgeProps,
  type Node,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import dagre from "dagre";
import type { BoardResponse, SpaceType } from "../api/types";
import { SPACE_TYPE_COLORS } from "../utils/spaceColors";

const NODE_WIDTH = 108;
const NODE_HEIGHT = 60;

interface BoardSpaceNodeData {
  index: number;
  spaceType: SpaceType;
  color: string;
  isStart: boolean;
  districtName?: string;
  [key: string]: unknown;
}

interface Point {
  x: number;
  y: number;
}

interface BoardPathEdgeData {
  // Waypoints dagre routed this edge through (see layout() below), in the same coordinate space
  // as node positions. Rendered as a rounded polyline instead of react-flow's default
  // source-to-target curve so long edges (e.g. the "loop back to start" edge every board has)
  // follow the lane dagre found for them instead of cutting straight across intervening nodes.
  points: Point[];
  [key: string]: unknown;
}

// ---- Public input shape: anything that can be turned into "one node per space, one edge per
// path" can be rendered by this component, whether that's a saved BoardResponse (see BoardGraph
// below) or an in-progress BoardCreatePage form (see BoardCreatePage.state.ts's
// buildGraphPreview). ----

export interface BoardGraphNodeInput {
  id: string;
  index: number;
  spaceType: SpaceType;
  color: string;
  isStart: boolean;
  districtName?: string;
}

export interface BoardGraphEdgeInput {
  id: string;
  source: string;
  target: string;
  branchOrder: number;
}

/**
 * Builds an SVG path that visits every point with rounded corners, by pulling back `radius` along
 * each incoming/outgoing segment at interior points and joining the pulled-back ends with a
 * quadratic curve centered on the original point. Degenerates to a straight line for 2 points.
 */
function roundedPolylinePath(points: Point[], radius = 14): string {
  if (points.length < 2) return "";
  if (points.length === 2) {
    return `M ${points[0].x},${points[0].y} L ${points[1].x},${points[1].y}`;
  }

  let d = `M ${points[0].x},${points[0].y}`;
  for (let i = 1; i < points.length - 1; i++) {
    const prev = points[i - 1];
    const curr = points[i];
    const next = points[i + 1];

    const toPrev = { x: prev.x - curr.x, y: prev.y - curr.y };
    const toNext = { x: next.x - curr.x, y: next.y - curr.y };
    const toPrevLen = Math.hypot(toPrev.x, toPrev.y) || 1;
    const toNextLen = Math.hypot(toNext.x, toNext.y) || 1;
    const r = Math.min(radius, toPrevLen / 2, toNextLen / 2);

    const enter = { x: curr.x + (toPrev.x / toPrevLen) * r, y: curr.y + (toPrev.y / toPrevLen) * r };
    const exit = { x: curr.x + (toNext.x / toNextLen) * r, y: curr.y + (toNext.y / toNextLen) * r };

    d += ` L ${enter.x},${enter.y} Q ${curr.x},${curr.y} ${exit.x},${exit.y}`;
  }
  const last = points[points.length - 1];
  d += ` L ${last.x},${last.y}`;
  return d;
}

/**
 * Runs dagre's layered layout over the board's spaces/paths. A finished board loops back to its
 * start space (see boardGraph.ts) and an in-progress one can be disconnected or even cyclic in
 * other ways, so this graph isn't necessarily a DAG -- dagre still produces a readable layout by
 * picking a feedback edge set for ranking, and for edges that span multiple ranks (which every
 * "loop back" edge does) it routes them through a chain of dummy nodes rather than drawing them
 * straight, so the ordering pass can push that chain into a clear lane instead of through other
 * spaces. We read those dummy-node waypoints back out (via graph.edge(...).points) and hand them
 * to a custom edge renderer -- without this, react-flow would ignore dagre's routing and draw a
 * straight line between the two real nodes regardless of what's in between.
 */
function layout(
  nodes: Node<BoardSpaceNodeData>[],
  edges: { id: string; source: string; target: string }[],
): { nodes: Node<BoardSpaceNodeData>[]; pointsByEdgeId: Map<string, Point[]> } {
  const graph = new dagre.graphlib.Graph({ multigraph: true });
  graph.setDefaultEdgeLabel(() => ({}));
  graph.setGraph({ rankdir: "LR", nodesep: 56, ranksep: 110, edgesep: 24 });

  for (const node of nodes) {
    graph.setNode(node.id, { width: NODE_WIDTH, height: NODE_HEIGHT });
  }
  for (const edge of edges) {
    // The label must be a real object, not `undefined` -- graphlib's setEdge treats an explicitly
    // passed `undefined` as "the caller specified this label" (it checks arguments.length, not
    // the value), which skips the default-label function entirely and leaves the edge with no
    // label object at all. dagre then crashes trying to write `.points` onto it after layout.
    graph.setEdge(edge.source, edge.target, {}, edge.id);
  }

  dagre.layout(graph);

  const laidOutNodes = nodes.map((node) => {
    const { x, y } = graph.node(node.id);
    return {
      ...node,
      position: { x: x - NODE_WIDTH / 2, y: y - NODE_HEIGHT / 2 },
      sourcePosition: Position.Right,
      targetPosition: Position.Left,
    };
  });

  const pointsByEdgeId = new Map<string, Point[]>();
  for (const edge of edges) {
    const dagreEdge = graph.edge({ v: edge.source, w: edge.target, name: edge.id });
    pointsByEdgeId.set(edge.id, dagreEdge?.points ?? []);
  }

  return { nodes: laidOutNodes, pointsByEdgeId };
}

function BoardSpaceNode({ data }: { data: BoardSpaceNodeData }) {
  return (
    <div
      className={`board-graph__node${data.isStart ? " board-graph__node--start" : ""}`}
      style={{ borderColor: data.color }}
      title={data.districtName ? `${data.spaceType} — ${data.districtName}` : data.spaceType}
    >
      <Handle type="target" position={Position.Left} />
      <span className="board-graph__node-index">{data.index}</span>
      <span className="board-graph__node-type" style={{ color: data.color }}>
        {data.spaceType}
      </span>
      {data.isStart && <span className="board-graph__node-badge">START</span>}
      <Handle type="source" position={Position.Right} />
    </div>
  );
}

function BoardPathEdge({ id, data, label, markerEnd, style }: EdgeProps<Edge<BoardPathEdgeData>>) {
  const points = data?.points ?? [];
  if (points.length < 2) return null;

  const path = roundedPolylinePath(points);
  const labelPoint = points[Math.floor(points.length / 2)];

  return (
    <>
      <BaseEdge id={id} path={path} markerEnd={markerEnd} style={style} />
      {label != null && (
        <EdgeLabelRenderer>
          <div
            className="board-graph__edge-label"
            style={{
              transform: `translate(-50%, -50%) translate(${labelPoint.x}px, ${labelPoint.y}px)`,
            }}
          >
            {label}
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  );
}

const nodeTypes = { boardSpace: BoardSpaceNode };
const edgeTypes = { boardPath: BoardPathEdge };

/**
 * Shared renderer: one node per BoardGraphNodeInput, one arrowed edge per BoardGraphEdgeInput,
 * routed through dagre's computed waypoints so edges hug the layout's lanes instead of cutting
 * through other spaces. A space with more than one outgoing path (a branch) gets its edges
 * labeled with branchOrder so the fork order is visible -- unbranched paths skip the label since
 * there's nothing to disambiguate.
 */
function GraphCanvas({
  nodes: nodeInputs,
  edges: edgeInputs,
  emptyMessage,
}: {
  nodes: BoardGraphNodeInput[];
  edges: BoardGraphEdgeInput[];
  emptyMessage: string;
}) {
  const outgoingCounts = useMemo(() => {
    const counts = new Map<string, number>();
    for (const edge of edgeInputs) {
      counts.set(edge.source, (counts.get(edge.source) ?? 0) + 1);
    }
    return counts;
  }, [edgeInputs]);

  const { nodes, edges } = useMemo(() => {
    const rawNodes: Node<BoardSpaceNodeData>[] = nodeInputs.map((input) => ({
      id: input.id,
      type: "boardSpace",
      position: { x: 0, y: 0 },
      data: {
        index: input.index,
        spaceType: input.spaceType,
        color: input.color,
        isStart: input.isStart,
        districtName: input.districtName,
      },
    }));

    const rawEdges = edgeInputs.map((input) => ({
      id: input.id,
      source: input.source,
      target: input.target,
      branched: (outgoingCounts.get(input.source) ?? 0) > 1,
      branchOrder: input.branchOrder,
    }));

    const { nodes: laidOutNodes, pointsByEdgeId } = layout(rawNodes, rawEdges);

    const finalEdges: Edge<BoardPathEdgeData>[] = rawEdges.map((edge) => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      type: "boardPath",
      label: edge.branched ? String(edge.branchOrder) : undefined,
      markerEnd: { type: MarkerType.ArrowClosed },
      data: { points: pointsByEdgeId.get(edge.id) ?? [] },
    }));

    return { nodes: laidOutNodes, edges: finalEdges };
  }, [nodeInputs, edgeInputs, outgoingCounts]);

  if (nodeInputs.length === 0) {
    return <p>{emptyMessage}</p>;
  }

  return (
    <div className="board-graph">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        fitView
        proOptions={{ hideAttribution: true }}
        nodesDraggable={false}
        nodesConnectable={false}
        elementsSelectable={false}
      >
        <Background gap={20} />
        <Controls showInteractive={false} />
      </ReactFlow>
    </div>
  );
}

/**
 * Renders a saved board's spaces and paths as a directed graph: one node per space (colored by
 * its district, or by space type when it has none).
 */
export function BoardGraph({ board }: { board: BoardResponse }) {
  const districtById = useMemo(
    () => new Map(board.districts.map((district) => [district.id, district])),
    [board.districts],
  );

  const nodes: BoardGraphNodeInput[] = useMemo(
    () =>
      board.spaces.map((space, index) => {
        const district = space.districtId ? districtById.get(space.districtId) : undefined;
        const color = district ? `#${district.colorHex}` : SPACE_TYPE_COLORS[space.spaceType];

        return {
          id: space.id,
          index,
          spaceType: space.spaceType,
          color,
          isStart: space.id === board.startSpaceId,
          districtName: district?.name,
        };
      }),
    [board.spaces, board.startSpaceId, districtById],
  );

  const edges: BoardGraphEdgeInput[] = useMemo(
    () =>
      board.paths.map((path, index) => ({
        id: `${path.from}-${path.to}-${path.branchOrder}-${index}`,
        source: path.from,
        target: path.to,
        branchOrder: path.branchOrder,
      })),
    [board.paths],
  );

  return <GraphCanvas nodes={nodes} edges={edges} emptyMessage="This board has no spaces yet." />;
}

/**
 * Renders an arbitrary node/edge set built elsewhere (e.g. BoardCreatePage.state.ts's
 * buildGraphPreview, for the live preview on the board creation page) using the same layout and
 * styling as BoardGraph.
 */
export function BoardGraphPreview({
  nodes,
  edges,
  emptyMessage,
}: {
  nodes: BoardGraphNodeInput[];
  edges: BoardGraphEdgeInput[];
  emptyMessage: string;
}) {
  return <GraphCanvas nodes={nodes} edges={edges} emptyMessage={emptyMessage} />;
}
