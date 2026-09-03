import { describe, expect, it } from "vitest";
import type { BoardResponse } from "../api/types";
import type { GameEvent } from "../api/gameProtocol";
import {
  applyGameEvent,
  describeEvent,
  initialGameState,
  netWorth,
  spaceLabel,
  summarizeCompletedTurns,
  winner,
} from "./gameState";

function board(): BoardResponse {
  return {
    id: "board-1",
    name: "Test board",
    startSpaceId: "space-0",
    startingGold: 1500,
    baseSalary: 200,
    promotionBonus: 100,
    spaces: [
      { id: "space-0", spaceType: "BASIC" },
      { id: "space-1", spaceType: "SHOP", baseValue: 100, basePricePercentage: 0.5 },
      { id: "space-2", spaceType: "BANK" },
    ],
    paths: [
      { from: "space-0", to: "space-1", branchOrder: 0 },
      { from: "space-1", to: "space-2", branchOrder: 0 },
      { from: "space-2", to: "space-0", branchOrder: 0 },
    ],
    districts: [{ id: "district-1", name: "Uptown", colorHex: "FF00AA", minimumStockPercentage: 0.1, progressions: [] }],
  };
}

function fold(events: GameEvent[]) {
  return events.reduce((state, event) => applyGameEvent(state, event), initialGameState(board(), ["p1", "p2"]));
}

describe("initialGameState", () => {
  it("seeds every player with the board's starting gold and no position", () => {
    const state = initialGameState(board(), ["p1", "p2"]);

    expect(state.players.p1).toMatchObject({ gold: 1500, currentSpaceId: null, ready: false });
    expect(state.players.p2).toMatchObject({ gold: 1500, currentSpaceId: null, ready: false });
    expect(state.phase).toBe("waiting_for_ready");
  });
});

describe("applyGameEvent", () => {
  it("places every player at the board's start space once the game starts", () => {
    const state = fold([{ type: "game_started", turnOrder: ["p1", "p2"] }]);

    expect(state.phase).toBe("in_progress");
    expect(state.players.p1.currentSpaceId).toBe("space-0");
    expect(state.players.p2.currentSpaceId).toBe("space-0");
  });

  it("moves a player without touching anyone else", () => {
    const state = fold([
      { type: "game_started", turnOrder: ["p1", "p2"] },
      {
        type: "player_moved",
        turnNumber: 0,
        playerId: "p1",
        fromSpaceId: "space-0",
        toSpaceId: "space-1",
        movementPointsRemaining: 0,
      },
    ]);

    expect(state.players.p1.currentSpaceId).toBe("space-1");
    expect(state.players.p2.currentSpaceId).toBe("space-0");
  });

  it("spends gold and records ownership on a shop purchase, then updates the shop's value", () => {
    const state = fold([
      { type: "shop_purchased", playerId: "p1", spaceId: "space-1", price: 250 },
    ]);

    expect(state.players.p1.gold).toBe(1500 - 250);
    expect(state.players.p1.ownedShopSpaceIds).toEqual(["space-1"]);
    expect(state.shopCurrentValueBySpaceId["space-1"]).toBe(250);
  });

  it("doesn't double-count a shop bought twice in the log (defensive against a replayed event)", () => {
    const state = fold([
      { type: "shop_purchased", playerId: "p1", spaceId: "space-1", price: 250 },
      { type: "shop_purchased", playerId: "p1", spaceId: "space-1", price: 250 },
    ]);

    expect(state.players.p1.ownedShopSpaceIds).toEqual(["space-1"]);
  });

  it("tracks stock purchases/sales and their running per-district price", () => {
    const state = fold([
      {
        type: "stock_purchased",
        playerId: "p1",
        districtId: "district-1",
        quantity: 10,
        pricePerShare: 20,
        totalCost: 200,
      },
      {
        type: "stock_sold",
        playerId: "p1",
        districtId: "district-1",
        quantity: 4,
        pricePerShare: 25,
        totalProceeds: 100,
      },
    ]);

    expect(state.players.p1.gold).toBe(1500 - 200 + 100);
    expect(state.players.p1.stockQuantitiesByDistrictId["district-1"]).toBe(6);
    expect(state.stockValueByDistrictId["district-1"]).toBe(25);
  });

  it("computes net worth from gold on hand plus owned shops and stock", () => {
    const state = fold([
      { type: "shop_purchased", playerId: "p1", spaceId: "space-1", price: 250 },
      {
        type: "stock_purchased",
        playerId: "p1",
        districtId: "district-1",
        quantity: 10,
        pricePerShare: 20,
        totalCost: 200,
      },
      {
        type: "district_values_recalculated",
        playerId: "p1",
        districtId: "district-1",
        newValuesBySpaceId: { "space-1": 300 },
      },
    ]);

    // Started with 1500 gold, spent 250 on the shop and 200 on stock -> 1050 left.
    // Shop now worth 300 (recalculated), plus 10 shares * 20/share = 200 in stock.
    expect(netWorth(state, "p1")).toBe(1050 + 300 + 200);
  });

  it("clears a pending prompt once it's resolved, but leaves it pending across an error", () => {
    const withPrompt = fold([
      { type: "shop_purchase_available", playerId: "p1", spaceId: "space-1", price: 250 },
    ]);
    expect(withPrompt.pendingPrompt).toEqual({
      kind: "shop_purchase",
      playerId: "p1",
      spaceId: "space-1",
      price: 250,
    });

    const afterError = applyGameEvent(withPrompt, { type: "error", message: "Can't afford it." });
    expect(afterError.pendingPrompt).toEqual(withPrompt.pendingPrompt);
    expect(afterError.lastError).toBe("Can't afford it.");

    const afterPurchase = applyGameEvent(afterError, {
      type: "shop_purchased",
      playerId: "p1",
      spaceId: "space-1",
      price: 250,
    });
    expect(afterPurchase.pendingPrompt).toBeNull();
  });

  it("doesn't add the same suit twice", () => {
    const state = fold([
      { type: "suit_picked_up", playerId: "p1", spaceId: "space-0", suit: "HEART" },
      { type: "suit_picked_up", playerId: "p1", spaceId: "space-0", suit: "HEART" },
    ]);

    expect(state.players.p1.heldSuits).toEqual(["HEART"]);
  });

  it("clears held suits and pays out on promotion", () => {
    const state = fold([
      { type: "suit_picked_up", playerId: "p1", spaceId: "space-0", suit: "HEART" },
      { type: "promoted", playerId: "p1", spaceId: "space-2", goldAwarded: 300 },
    ]);

    expect(state.players.p1.heldSuits).toEqual([]);
    expect(state.players.p1.promotionCount).toBe(1);
    expect(state.players.p1.gold).toBe(1500 + 300);
  });

  it("ignores an event for a player it doesn't know about instead of throwing", () => {
    expect(() => fold([{ type: "player_ready", playerId: "unknown-player" }])).not.toThrow();
  });
});

describe("applyGameEvent: game_state (reconnect snapshot)", () => {
  it("hydrates a fresh connection to a game already in progress", () => {
    const state = fold([
      {
        type: "game_state",
        turnOrder: ["p2", "p1"],
        turnNumber: 3,
        gameOver: false,
        activePlayerId: "p1",
        pendingChoiceRequired: null,
        pendingShopPurchaseAvailable: null,
        pendingStockTradingAvailable: null,
        players: [
          {
            playerId: "p1",
            ready: true,
            currentSpaceId: "space-1",
            currentGold: 1200,
            heldSuits: ["HEART"],
            promotionCount: 1,
            ownedShopSpaceIds: ["space-1"],
            stockQuantitiesByDistrictId: { "district-1": 5 },
          },
          {
            playerId: "p2",
            ready: true,
            currentSpaceId: "space-0",
            currentGold: 1500,
            heldSuits: [],
            promotionCount: 0,
            ownedShopSpaceIds: [],
            stockQuantitiesByDistrictId: {},
          },
        ],
        shopValuesBySpaceId: { "space-1": 400 },
        stockValuesByDistrictId: { "district-1": 30 },
      },
    ]);

    expect(state.phase).toBe("in_progress");
    expect(state.turnOrder).toEqual(["p2", "p1"]);
    expect(state.turnNumber).toBe(3);
    expect(state.activePlayerId).toBe("p1");
    expect(state.players.p1).toMatchObject({
      currentSpaceId: "space-1",
      gold: 1200,
      heldSuits: ["HEART"],
      promotionCount: 1,
      ownedShopSpaceIds: ["space-1"],
    });
    // 1200 gold on hand, plus the shop (400) plus 5 shares * 30/share = 150 in stock.
    expect(netWorth(state, "p1")).toBe(1200 + 400 + 5 * 30);
    expect(state.pendingPrompt).toBeNull();
  });

  it("restores whichever decision was left pending, exactly as the live event would have", () => {
    const state = fold([
      {
        type: "game_state",
        turnOrder: ["p1", "p2"],
        turnNumber: 0,
        gameOver: false,
        activePlayerId: "p1",
        pendingChoiceRequired: null,
        pendingShopPurchaseAvailable: {
          type: "shop_purchase_available",
          playerId: "p1",
          spaceId: "space-1",
          price: 250,
        },
        pendingStockTradingAvailable: null,
        players: [
          {
            playerId: "p1",
            ready: true,
            currentSpaceId: "space-1",
            currentGold: 1500,
            heldSuits: [],
            promotionCount: 0,
            ownedShopSpaceIds: [],
            stockQuantitiesByDistrictId: {},
          },
          {
            playerId: "p2",
            ready: true,
            currentSpaceId: "space-0",
            currentGold: 1500,
            heldSuits: [],
            promotionCount: 0,
            ownedShopSpaceIds: [],
            stockQuantitiesByDistrictId: {},
          },
        ],
        shopValuesBySpaceId: {},
        stockValuesByDistrictId: {},
      },
    ]);

    expect(state.pendingPrompt).toEqual({
      kind: "shop_purchase",
      playerId: "p1",
      spaceId: "space-1",
      price: 250,
    });
  });

  it("reflects a game that hasn't started, and one that's already over", () => {
    const notStarted = fold([
      {
        type: "game_state",
        turnOrder: null,
        turnNumber: 0,
        gameOver: false,
        activePlayerId: null,
        pendingChoiceRequired: null,
        pendingShopPurchaseAvailable: null,
        pendingStockTradingAvailable: null,
        players: [],
        shopValuesBySpaceId: {},
        stockValuesByDistrictId: {},
      },
    ]);
    expect(notStarted.phase).toBe("waiting_for_ready");

    const over = fold([
      {
        type: "game_state",
        turnOrder: ["p1", "p2"],
        turnNumber: 10,
        gameOver: true,
        activePlayerId: null,
        pendingChoiceRequired: null,
        pendingShopPurchaseAvailable: null,
        pendingStockTradingAvailable: null,
        players: [],
        shopValuesBySpaceId: {},
        stockValuesByDistrictId: {},
      },
    ]);
    expect(over.phase).toBe("game_over");
  });
});

describe("winner", () => {
  it("is null before the game is over", () => {
    const state = fold([
      { type: "game_started", turnOrder: ["p1", "p2"] },
      { type: "shop_purchased", playerId: "p1", spaceId: "space-1", price: 250 },
    ]);

    expect(winner(state)).toBeNull();
  });

  it("picks whoever has the highest net worth once the game ends", () => {
    const state = fold([
      { type: "game_started", turnOrder: ["p1", "p2"] },
      // p1: 1500 - 250 = 1250 gold, plus a 250 shop = 1500 net worth.
      { type: "shop_purchased", playerId: "p1", spaceId: "space-1", price: 250 },
      // p2: 1500 gold untouched = 1500 net worth, then 300 more from a promotion payout.
      { type: "promoted", playerId: "p2", spaceId: "space-2", goldAwarded: 300 },
      { type: "game_over", turnCount: 10 },
    ]);

    expect(winner(state)?.id).toBe("p2");
  });

  it("breaks a net-worth tie by turn order", () => {
    const state = fold([
      { type: "game_started", turnOrder: ["p1", "p2"] },
      { type: "game_over", turnCount: 10 },
    ]);

    // Both players are still sitting on the same starting gold with nothing else -- p1 comes
    // first in turnOrder, so it wins the tie.
    expect(winner(state)?.id).toBe("p1");
  });
});

describe("spaceLabel / describeEvent", () => {
  it("labels a known space by index and type, and falls back to the raw id otherwise", () => {
    expect(spaceLabel(board(), "space-1")).toBe("#1 SHOP");
    expect(spaceLabel(board(), "space-404")).toBe("space-404");
  });

  it("renders a readable line using the caller's player labels", () => {
    const text = describeEvent(
      { type: "player_moved", turnNumber: 0, playerId: "p1", fromSpaceId: "space-0", toSpaceId: "space-1", movementPointsRemaining: 0 },
      board(),
      (id) => (id === "p1" ? "Alice" : id),
    );
    expect(text).toBe("Alice moved to #1 SHOP.");
  });
});


describe("applyGameEvent: dice_rolled (last roll display)", () => {
  it("records the roll against the player who rolled it", () => {
    const state = fold([{ type: "dice_rolled", playerId: "p1", roll: 5 }]);

    expect(state.lastRoll).toEqual({ playerId: "p1", roll: 5 });
    expect(state.activePlayerId).toBe("p1");
  });

  it("is overwritten by the next roll, whoever rolls it", () => {
    const state = fold([
      { type: "dice_rolled", playerId: "p1", roll: 5 },
      { type: "dice_rolled", playerId: "p2", roll: 2 },
    ]);

    expect(state.lastRoll).toEqual({ playerId: "p2", roll: 2 });
  });

  it("is cleared by a game_state snapshot, since a fresh sync doesn't know it", () => {
    const state = fold([
      { type: "dice_rolled", playerId: "p1", roll: 5 },
      {
        type: "game_state",
        turnOrder: ["p1", "p2"],
        turnNumber: 0,
        gameOver: false,
        activePlayerId: "p1",
        pendingChoiceRequired: null,
        pendingShopPurchaseAvailable: null,
        pendingStockTradingAvailable: null,
        players: [
          {
            playerId: "p1",
            ready: true,
            currentSpaceId: "space-0",
            currentGold: 1500,
            heldSuits: [],
            promotionCount: 0,
            ownedShopSpaceIds: [],
            stockQuantitiesByDistrictId: {},
          },
          {
            playerId: "p2",
            ready: true,
            currentSpaceId: "space-0",
            currentGold: 1500,
            heldSuits: [],
            promotionCount: 0,
            ownedShopSpaceIds: [],
            stockQuantitiesByDistrictId: {},
          },
        ],
        shopValuesBySpaceId: {},
        stockValuesByDistrictId: {},
      },
    ]);

    expect(state.lastRoll).toBeNull();
  });
});

describe("summarizeCompletedTurns", () => {
  it("condenses a turn's events into one headline, segmented by turn_ended", () => {
    const summaries = summarizeCompletedTurns(
      [
        { type: "turn_started", playerId: "p1", turnNumber: 1 },
        { type: "dice_rolled", playerId: "p1", roll: 4 },
        {
          type: "player_moved",
          turnNumber: 1,
          playerId: "p1",
          fromSpaceId: "space-0",
          toSpaceId: "space-1",
          movementPointsRemaining: 0,
        },
        { type: "shop_purchase_available", playerId: "p1", spaceId: "space-1", price: 100 },
        { type: "shop_purchased", playerId: "p1", spaceId: "space-1", price: 100 },
        { type: "turn_ended", turnNumber: 1, playerId: "p1" },
      ],
      board(),
    );

    expect(summaries).toEqual([
      {
        turnNumber: 1,
        playerId: "p1",
        headline: "Rolled a 4, moved to #1 SHOP, bought #1 SHOP for 100 gold.",
      },
    ]);
  });

  it('summarizes a turn with no notable actions as "Passed."', () => {
    const summaries = summarizeCompletedTurns(
      [{ type: "turn_ended", turnNumber: 1, playerId: "p1" }],
      board(),
    );

    expect(summaries).toEqual([{ turnNumber: 1, playerId: "p1", headline: "Passed." }]);
  });

  it("keeps separate turns separate, in order", () => {
    const summaries = summarizeCompletedTurns(
      [
        { type: "dice_rolled", playerId: "p1", roll: 2 },
        { type: "turn_ended", turnNumber: 1, playerId: "p1" },
        { type: "dice_rolled", playerId: "p2", roll: 3 },
        { type: "turn_ended", turnNumber: 2, playerId: "p2" },
      ],
      board(),
    );

    expect(summaries.map((s) => s.turnNumber)).toEqual([1, 2]);
    expect(summaries.map((s) => s.playerId)).toEqual(["p1", "p2"]);
  });
});
