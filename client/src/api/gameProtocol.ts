// TypeScript mirrors of the server's websocket protocol (see
// service/src/main/kotlin/com/fortuneavenue/server/websocket/{ClientMessage,GameEvent}.kt).
// Kept separate from api/types.ts, which only covers the REST DTOs.

// ---- Messages the client sends ----

export const ClientMessageType = {
  READY: "ready",
  ROLL_DICE: "roll_dice",
  CHOOSE_PATH: "choose_path",
  BUY_SHOP: "buy_shop",
  DECLINE_SHOP: "decline_shop",
  BUY_STOCK: "buy_stock",
  SELL_STOCK: "sell_stock",
  SKIP_STOCK_TRADE: "skip_stock_trade",
} as const;

/**
 * Every message the client sends is `{"type": "..."}` plus whatever extra fields that type needs
 * -- gameId/playerId are already implied by the connection itself (see gameSocket.ts). Mirrors
 * ClientMessage.kt exactly, including which fields go with which type: choose_path needs spaceId;
 * buy_stock/sell_stock need districtId and quantity (1-99); the rest need nothing extra.
 */
export type ClientMessage =
  | { type: typeof ClientMessageType.READY }
  | { type: typeof ClientMessageType.ROLL_DICE }
  | { type: typeof ClientMessageType.CHOOSE_PATH; spaceId: string }
  | { type: typeof ClientMessageType.BUY_SHOP }
  | { type: typeof ClientMessageType.DECLINE_SHOP }
  | { type: typeof ClientMessageType.BUY_STOCK; districtId: string; quantity: number }
  | { type: typeof ClientMessageType.SELL_STOCK; districtId: string; quantity: number }
  | { type: typeof ClientMessageType.SKIP_STOCK_TRADE };

// ---- Events the server sends back ----

export interface ConnectedEvent {
  type: "connected";
  playerId: string;
}

export interface PlayerReadyEvent {
  type: "player_ready";
  playerId: string;
}

export interface GameStartedEvent {
  type: "game_started";
  turnOrder: string[];
}

export interface DiceRolledEvent {
  type: "dice_rolled";
  playerId: string;
  roll: number;
}

export interface PlayerMovedEvent {
  type: "player_moved";
  turnNumber: number;
  playerId: string;
  fromSpaceId: string;
  toSpaceId: string;
  movementPointsRemaining: number;
}

/** playerId picked up suit (HEART/DIAMOND/SPADE/CLUB) by passing or landing on spaceId. */
export interface SuitPickedUpEvent {
  type: "suit_picked_up";
  playerId: string;
  spaceId: string;
  suit: string;
}

/**
 * playerId was promoted at spaceId (a BANK space) after passing or landing on it while holding all
 * 4 suits -- their held suits have been cleared, their promotion count went up by one, and they
 * were paid goldAwarded gold.
 */
export interface PromotedEvent {
  type: "promoted";
  playerId: string;
  spaceId: string;
  goldAwarded: number;
}

/** One outgoing path a player can pick with a choose_path message. */
export interface PathOptionPayload {
  toSpaceId: string;
  branchOrder: number;
}

export interface ChoiceRequiredEvent {
  type: "choice_required";
  playerId: string;
  spaceId: string;
  options: PathOptionPayload[];
  /** How many spaces the player still has left to move after whichever option they pick. */
  movementPointsRemaining: number;
}

export interface ShopPurchaseAvailableEvent {
  type: "shop_purchase_available";
  playerId: string;
  spaceId: string;
  price: number;
}

export interface ShopPurchasedEvent {
  type: "shop_purchased";
  playerId: string;
  spaceId: string;
  price: number;
}

/** newValuesBySpaceId maps each recalculated shop's spaceId to its new currentValue. */
export interface DistrictValuesRecalculatedEvent {
  type: "district_values_recalculated";
  playerId: string;
  districtId: string;
  newValuesBySpaceId: Record<string, number>;
}

/** One district's stock a player can buy or sell with a buy_stock/sell_stock message. */
export interface StockTradeOfferPayload {
  districtId: string;
  pricePerShare: number;
  ownedQuantity: number;
}

export interface StockTradingAvailableEvent {
  type: "stock_trading_available";
  playerId: string;
  spaceId: string;
  offers: StockTradeOfferPayload[];
}

export interface StockPurchasedEvent {
  type: "stock_purchased";
  playerId: string;
  districtId: string;
  quantity: number;
  pricePerShare: number;
  totalCost: number;
}

export interface StockSoldEvent {
  type: "stock_sold";
  playerId: string;
  districtId: string;
  quantity: number;
  pricePerShare: number;
  totalProceeds: number;
}

export interface TurnEndedEvent {
  type: "turn_ended";
  turnNumber: number;
  playerId: string;
}

/** It's playerId's turn and they need to roll -- nothing else announces this for them. */
export interface TurnStartedEvent {
  type: "turn_started";
  playerId: string;
  turnNumber: number;
}

export interface GameOverEvent {
  type: "game_over";
  turnCount: number;
}

export interface ErrorEvent {
  type: "error";
  message: string;
}

export interface PlayerSnapshotPayload {
  playerId: string;
  ready: boolean;
  currentSpaceId: string | null;
  currentGold: number;
  heldSuits: string[];
  promotionCount: number;
  ownedShopSpaceIds: string[];
  stockQuantitiesByDistrictId: Record<string, number>;
}

/**
 * Sent once, right after `connected`, so a client connecting (or reconnecting) partway through a
 * game doesn't have to have seen every event live to know where things stand -- see
 * GameSimulationService.getSnapshot (server-side). pendingChoiceRequired/
 * pendingShopPurchaseAvailable/pendingStockTradingAvailable deliberately reuse those events' own
 * shape rather than a new one, so gameState.ts can fold whichever is non-null onto state the exact
 * same way it would the live event that originally caused that pause -- at most one is ever
 * non-null, naming whatever activePlayerId currently has movement paused on.
 */
export interface GameStateSnapshotEvent {
  type: "game_state";
  turnOrder: string[] | null;
  turnNumber: number;
  gameOver: boolean;
  activePlayerId: string | null;
  pendingChoiceRequired: ChoiceRequiredEvent | null;
  pendingShopPurchaseAvailable: ShopPurchaseAvailableEvent | null;
  pendingStockTradingAvailable: StockTradingAvailableEvent | null;
  players: PlayerSnapshotPayload[];
  shopValuesBySpaceId: Record<string, number>;
  stockValuesByDistrictId: Record<string, number>;
}

export type GameEvent =
  | ConnectedEvent
  | PlayerReadyEvent
  | GameStartedEvent
  | DiceRolledEvent
  | PlayerMovedEvent
  | SuitPickedUpEvent
  | PromotedEvent
  | ChoiceRequiredEvent
  | ShopPurchaseAvailableEvent
  | ShopPurchasedEvent
  | DistrictValuesRecalculatedEvent
  | StockTradingAvailableEvent
  | StockPurchasedEvent
  | StockSoldEvent
  | TurnEndedEvent
  | TurnStartedEvent
  | GameOverEvent
  | ErrorEvent
  | GameStateSnapshotEvent;

const GAME_EVENT_TYPES: ReadonlySet<GameEvent["type"]> = new Set([
  "connected",
  "player_ready",
  "game_started",
  "dice_rolled",
  "player_moved",
  "suit_picked_up",
  "promoted",
  "choice_required",
  "shop_purchase_available",
  "shop_purchased",
  "district_values_recalculated",
  "stock_trading_available",
  "stock_purchased",
  "stock_sold",
  "turn_ended",
  "turn_started",
  "game_over",
  "error",
  "game_state",
] satisfies GameEvent["type"][]);

/**
 * Parses one incoming websocket text frame into a GameEvent, or null if it isn't recognized (a
 * protocol change on the server, or plain garbage) -- callers should ignore rather than crash on
 * null, the same way the server itself ignores an unparseable ClientMessage.
 */
export function parseGameEvent(raw: string): GameEvent | null {
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return null;
  }

  if (
    typeof parsed !== "object" ||
    parsed === null ||
    !("type" in parsed) ||
    typeof (parsed as { type: unknown }).type !== "string" ||
    !GAME_EVENT_TYPES.has((parsed as { type: GameEvent["type"] }).type)
  ) {
    return null;
  }

  return parsed as GameEvent;
}
