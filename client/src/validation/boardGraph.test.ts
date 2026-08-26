import { describe, expect, it } from "vitest";
import { type GraphEdge, validateBoardGraph } from "./boardGraph";

describe("validateBoardGraph", () => {
  it("requires at least one space", () => {
    expect(validateBoardGraph(0, [], 0)).toEqual(["A board needs at least one space."]);
  });

  it("rejects a start index outside the space range", () => {
    expect(validateBoardGraph(3, [], 5)).toEqual([
      "Start space index 5 is out of range for 3 spaces.",
    ]);
  });

  it("rejects an edge that references a space index outside the range", () => {
    const edges: GraphEdge[] = [{ from: 0, to: 5 }];
    expect(validateBoardGraph(2, edges, 0)).toEqual([
      "One or more paths reference a space index outside the valid range.",
    ]);
  });

  it("accepts a fully connected loop with no errors", () => {
    const edges: GraphEdge[] = [
      { from: 0, to: 1 },
      { from: 1, to: 2 },
      { from: 2, to: 0 },
    ];
    expect(validateBoardGraph(3, edges, 0)).toEqual([]);
  });

  it("flags a space that isn't reachable from the start, and can't get back to it", () => {
    // 0 -> 1, and nothing else -- space 2 is completely disconnected.
    const edges: GraphEdge[] = [{ from: 0, to: 1 }];
    expect(validateBoardGraph(3, edges, 0)).toEqual([
      "Space(s) at index [2] are not reachable from the start space.",
      "Space(s) at index [1, 2] can never make it back to the start space.",
    ]);
  });

  it("flags a dead end that's reachable from the start but can't loop back to it", () => {
    // A fork with no way back: 0 -> 1 and 0 -> 2, but nothing returns to 0.
    const edges: GraphEdge[] = [
      { from: 0, to: 1 },
      { from: 0, to: 2 },
    ];
    expect(validateBoardGraph(3, edges, 0)).toEqual([
      "Space(s) at index [1, 2] can never make it back to the start space.",
    ]);
  });

  it("accepts the sample-loop-with-branch fixture (sample_data/example-board.json)", () => {
    const edges: GraphEdge[] = [
      { from: 0, to: 1 },
      { from: 1, to: 2 },
      { from: 2, to: 3 },
      { from: 2, to: 6 },
      { from: 3, to: 4 },
      { from: 6, to: 7 },
      { from: 7, to: 4 },
      { from: 4, to: 5 },
      { from: 5, to: 0 },
    ];
    expect(validateBoardGraph(8, edges, 0)).toEqual([]);
  });
});
