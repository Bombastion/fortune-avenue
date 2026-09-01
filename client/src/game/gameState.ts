// Pure client-side model of an in-progress game, built by folding the websocket event stream (see
// api/gameProtocol.ts) onto itself. There's no REST endpoint for "current game state" (session
// state lives only in the events themselves -- see GameWebSocketHandler.kt), so everything here --
// position, gold, held suits, promotions, shop/stock ownership, net worth -- is reconstructed from
// events alone, seeded with the one thing we *do* get for free: every player starts at the board's
// start space with the board's startingGold. Kept separate from useGameSocket.ts (which owns the
// actual WebSocket connection) so this reducer can be tested without one, the same way
// BoardCreatePage.state.ts's pure functions are.
import type { BoardResponse } from "../api/types";
import type { GameEvent, PathOptionPayload, StockTradeOfferPayload } from "../api/gameProtocol";

export interface PlayerGameState {
  id: string;
  ready: boolean;
  // Null until game_started -- mirrors the server's own PlayerState.currentSpaceId, which stays
  // null until a player's first move (see GameSimulationService, which falls back to
  // board.startSpaceId itself whenever this is null).
  currentSpaceId: string | null;
  gold: number;
  heldSuits: string[];
  promotionCount: number;
  ownedShopSpaceIds: string[];
  stockQuantitiesByDistrictId: Record<string, number>;
}

export type PendingPrompt =
  | { kind: "choose_path"; playerId: string; spaceId: string; options: PathOptionPayload[] }
  | { kind: "shop_purchase"; playerId: string; spaceId: string; price: number }
  | { kind: "stock_trade"; playerId: string; spaceId: string; offers: StockTradeOfferPayload[] };

export type GamePhase = "waiting_for_ready" | "in_progress" | "game_over";

const MAX_LOG_ENTRIES = 200;

export interface GameState {
  board: BoardResponse;
  phase: GamePhase;
  turnOrder: string[] | null;
  turnNumber: number;
  // Whoever's turn is currently active, human or computer -- set by dice_rolled (the one event
  // every turn, human or computer, always produces) and turn_started (the one event a *human*
  // turn also gets, since nothing else would prompt them to roll).
  activePlayerId: string | null;
  pendingPrompt: PendingPrompt | null;
  lastError: string | null;
  // The most recent dice_rolled we've seen, kept around purely for display -- callers should
  // only show it while lastRoll.playerId === activePlayerId, since that's what keeps it from
  // bleeding into the next player's turn (see the activePlayerId doc comment: dice_rolled is the
  // one event that fires for every turn, human or computer, so it's what actually flips
  // activePlayerId over).
  lastRoll: { playerId: string; roll: number } | null;
  players: Record<string, PlayerGameState>;
  // A shop's value starts out unknown to us (the server computes it from baseValue/
  // basePricePercentage plus district ownership boosts we don't replicate) until either it's
  // bought (shop_purchased.price) or its district recalculates (district_values_recalculated).
  shopCurrentValueBySpaceId: Record<string, number>;
  // Same idea for a district's per-share stock price, learned from whichever of
  // stock_trading_available/stock_purchased/stock_sold we've seen most recently for it.
  stockValueByDistrictId: Record<string, number>;
  // Capped raw event history, newest last -- see describeEvent for turning these into text.
  log: GameEvent[];
}

export function newPlayerGameState(id: string): PlayerGameState {
  return {
    id,
    ready: false,
    currentSpaceId: null,
    gold: 0,
    heldSuits: [],
    promotionCount: 0,
    ownedShopSpaceIds: [],
    stockQuantitiesByDistrictId: {},
  };
}

export function initialGameState(board: BoardResponse, playerIds: string[]): GameState {
  const players: Record<string, PlayerGameState> = {};
  for (const id of playerIds) {
    players[id] = { ...newPlayerGameState(id), gold: board.startingGold };
  }

  return {
    board,
    phase: "waiting_for_ready",
    turnOrder: null,
    turnNumber: 0,
    activePlayerId: null,
    pendingPrompt: null,
    lastError: null,
    lastRoll: null,
    players,
    shopCurrentValueBySpaceId: {},
    stockValueByDistrictId: {},
    log: [],
  };
}

/** A player's net worth: every shop they own plus the current value of every stock they hold,
 * gold on hand deliberately excluded -- mirrors GameSimulationService.netWorth() exactly (modulo
 * shop/stock values we haven't learned yet from events, which count as 0 until we do). */
export function netWorth(state: GameState, playerId: string): number {
  const player = state.players[playerId];
  if (!player) return 0;

  const shopValue = player.ownedShopSpaceIds.reduce(
    (sum, spaceId) => sum + (state.shopCurrentValueBySpaceId[spaceId] ?? 0),
    0,
  );
  const stockValue = Object.entries(player.stockQuantitiesByDistrictId).reduce(
    (sum, [districtId, quantity]) => sum + quantity * (state.stockValueByDistrictId[districtId] ?? 0),
    0,
  );
  return shopValue + stockValue;
}

/** `#3 SHOP`, or just the id if it's not on this board (shouldn't happen, but events are external
 * input -- never trust them to be internally consistent). */
export function spaceLabel(board: BoardResponse, spaceId: string): string {
  const index = board.spaces.findIndex((space) => space.id === spaceId);
  if (index === -1) return spaceId;
  return `#${index} ${board.spaces[index].spaceType}`;
}

function updatePlayer(
  players: Record<string, PlayerGameState>,
  playerId: string,
  update: (player: PlayerGameState) => PlayerGameState,
): Record<string, PlayerGameState> {
  const player = players[playerId];
  if (!player) return players;
  return { ...players, [playerId]: update(player) };
}

function pushLog(log: GameEvent[], event: GameEvent): GameEvent[] {
  const next = [...log, event];
  return next.length > MAX_LOG_ENTRIES ? next.slice(next.length - MAX_LOG_ENTRIES) : next;
}

/**
 * Folds one event onto the current state. Events that resolve a pending prompt (or that simply
 * couldn't co-occur with one, like movement) clear pendingPrompt; the three "*_available"/
 * "choice_required" events set a new one; `error` is the one type that deliberately leaves
 * whatever was pending untouched, since an error means the action was rejected and the decision
 * is, per the server's own contract, "left still pending".
 */
export function applyGameEvent(state: GameState, event: GameEvent): GameState {
  const log = pushLog(state.log, event);

  switch (event.type) {
    case "connected":
      return { ...state, log };

    case "player_ready":
      return {
        ...state,
        log,
        players: updatePlayer(state.players, event.playerId, (p) => ({ ...p, ready: true })),
      };

    case "game_started": {
      const startSpaceId = state.board.startSpaceId;
      let players = state.players;
      for (const playerId of event.turnOrder) {
        players = updatePlayer(players, playerId, (p) => ({
          ...p,
          ready: true,
          currentSpaceId: p.currentSpaceId ?? startSpaceId,
        }));
      }
      return {
        ...state,
        log,
        phase: "in_progress",
        turnOrder: event.turnOrder,
        pendingPrompt: null,
        players,
      };
    }

    case "dice_rolled":
      return {
        ...state,
        log,
        activePlayerId: event.playerId,
        pendingPrompt: null,
        lastRoll: { playerId: event.playerId, roll: event.roll },
      };

    case "player_moved":
      return {
        ...state,
        log,
        pendingPrompt: null,
        players: updatePlayer(state.players, event.playerId, (p) => ({
          ...p,
          currentSpaceId: event.toSpaceId,
        })),
      };

    case "suit_picked_up":
      return {
        ...state,
        log,
        pendingPrompt: null,
        players: updatePlayer(state.players, event.playerId, (p) =>
          p.heldSuits.includes(event.suit) ? p : { ...p, heldSuits: [...p.heldSuits, event.suit] },
        ),
      };

    case "promoted":
      return {
        ...state,
        log,
        pendingPrompt: null,
        players: updatePlayer(state.players, event.playerId, (p) => ({
          ...p,
          heldSuits: [],
          promotionCount: p.promotionCount + 1,
          gold: p.gold + event.goldAwarded,
        })),
      };

    case "choice_required":
      return {
        ...state,
        log,
        pendingPrompt: {
          kind: "choose_path",
          playerId: event.playerId,
          spaceId: event.spaceId,
          options: event.options,
        },
      };

    case "shop_purchase_available":
      return {
        ...state,
        log,
        pendingPrompt: {
          kind: "shop_purchase",
          playerId: event.playerId,
          spaceId: event.spaceId,
          price: event.price,
        },
      };

    case "shop_purchased":
      return {
        ...state,
        log,
        pendingPrompt: null,
        shopCurrentValueBySpaceId: {
          ...state.shopCurrentValueBySpaceId,
          [event.spaceId]: event.price,
        },
        players: updatePlayer(state.players, event.playerId, (p) => ({
          ...p,
          gold: p.gold - event.price,
          ownedShopSpaceIds: p.ownedShopSpaceIds.includes(event.spaceId)
            ? p.ownedShopSpaceIds
            : [...p.ownedShopSpaceIds, event.spaceId],
        })),
      };

    case "district_values_recalculated":
      return {
        ...state,
        log,
        pendingPrompt: null,
        shopCurrentValueBySpaceId: {
          ...state.shopCurrentValueBySpaceId,
          ...event.newValuesBySpaceId,
        },
      };

    case "stock_trading_available": {
      const stockValueByDistrictId = { ...state.stockValueByDistrictId };
      for (const offer of event.offers) {
        stockValueByDistrictId[offer.districtId] = offer.pricePerShare;
      }
      return {
        ...state,
        log,
        stockValueByDistrictId,
        pendingPrompt: {
          kind: "stock_trade",
          playerId: event.playerId,
          spaceId: event.spaceId,
          offers: event.offers,
        },
      };
    }

    case "stock_purchased":
      return {
        ...state,
        log,
        pendingPrompt: null,
        stockValueByDistrictId: {
          ...state.stockValueByDistrictId,
          [event.districtId]: event.pricePerShare,
        },
        players: updatePlayer(state.players, event.playerId, (p) => ({
          ...p,
          gold: p.gold - event.totalCost,
          stockQuantitiesByDistrictId: {
            ...p.stockQuantitiesByDistrictId,
            [event.districtId]: (p.stockQuantitiesByDistrictId[event.districtId] ?? 0) + event.quantity,
          },
        })),
      };

    case "stock_sold":
      return {
        ...state,
        log,
        pendingPrompt: null,
        stockValueByDistrictId: {
          ...state.stockValueByDistrictId,
          [event.districtId]: event.pricePerShare,
        },
        players: updatePlayer(state.players, event.playerId, (p) => ({
          ...p,
          gold: p.gold + event.totalProceeds,
          stockQuantitiesByDistrictId: {
            ...p.stockQuantitiesByDistrictId,
            [event.districtId]: (p.stockQuantitiesByDistrictId[event.districtId] ?? 0) - event.quantity,
          },
        })),
      };

    case "turn_ended":
      return { ...state, log, pendingPrompt: null, turnNumber: event.turnNumber };

    case "turn_started":
      return {
        ...state,
        log,
        pendingPrompt: null,
        turnNumber: event.turnNumber,
        activePlayerId: event.playerId,
      };

    case "game_over":
      return { ...state, log, phase: "game_over", pendingPrompt: null, activePlayerId: null };

    case "error":
      // Deliberately does NOT clear pendingPrompt -- see the doc comment above.
      return { ...state, log, lastError: event.message };

    case "game_state": {
      const players: Record<string, PlayerGameState> = {};
      for (const p of event.players) {
        players[p.playerId] = {
          id: p.playerId,
          ready: p.ready,
          currentSpaceId: p.currentSpaceId,
          gold: p.currentGold,
          heldSuits: [...p.heldSuits],
          promotionCount: p.promotionCount,
          ownedShopSpaceIds: [...p.ownedShopSpaceIds],
          stockQuantitiesByDistrictId: { ...p.stockQuantitiesByDistrictId },
        };
      }

      let next: GameState = {
        ...state,
        log,
        phase:
          event.turnOrder === null ? "waiting_for_ready" : event.gameOver ? "game_over" : "in_progress",
        turnOrder: event.turnOrder,
        turnNumber: event.turnNumber,
        activePlayerId: event.activePlayerId,
        pendingPrompt: null,
        // Unknown after a fresh sync -- the snapshot doesn't carry a die roll, only where things
        // landed, so there's nothing here to show until the next dice_rolled comes in live.
        lastRoll: null,
        players,
        shopCurrentValueBySpaceId: { ...event.shopValuesBySpaceId },
        stockValueByDistrictId: { ...event.stockValuesByDistrictId },
      };

      // Reuse the exact same prompt-setting branches above for whichever pause (if any) is
      // currently active, rather than duplicating that logic here -- at most one of these three
      // is ever non-null (see GameStateSnapshotEvent's own doc comment).
      if (event.pendingChoiceRequired) next = applyGameEvent(next, event.pendingChoiceRequired);
      if (event.pendingShopPurchaseAvailable) {
        next = applyGameEvent(next, event.pendingShopPurchaseAvailable);
      }
      if (event.pendingStockTradingAvailable) {
        next = applyGameEvent(next, event.pendingStockTradingAvailable);
      }

      return next;
    }

    default:
      return { ...state, log };
  }
}

/** One turn's worth of gameplay action, condensed into a single line -- see
 * summarizeCompletedTurns. */
export interface TurnSummary {
  turnNumber: number;
  playerId: string;
  headline: string;
}

/** The event types summarizeCompletedTurns treats as "something happened this turn" -- prompts
 * like choice_required/shop_purchase_available/stock_trading_available are deliberately excluded
 * since they describe a pause, not an action taken, and district_values_recalculated is a side
 * effect of shop_purchased rather than a distinct action. */
function describeTurnAction(event: GameEvent, board: BoardResponse): string | null {
  switch (event.type) {
    case "dice_rolled":
      return `rolled a ${event.roll}`;
    case "player_moved":
      return `moved to ${spaceLabel(board, event.toSpaceId)}`;
    case "suit_picked_up":
      return `picked up the ${event.suit} suit`;
    case "promoted":
      return `was promoted at ${spaceLabel(board, event.spaceId)} (+${event.goldAwarded} gold)`;
    case "shop_purchased":
      return `bought ${spaceLabel(board, event.spaceId)} for ${event.price} gold`;
    case "stock_purchased":
      return `bought ${event.quantity} share(s) of stock for ${event.totalCost} gold`;
    case "stock_sold":
      return `sold ${event.quantity} share(s) of stock for ${event.totalProceeds} gold`;
    default:
      return null;
  }
}

function capitalize(text: string): string {
  return text.length === 0 ? text : text.charAt(0).toUpperCase() + text.slice(1);
}

/**
 * Groups the raw event log into one summary per completed turn, segmented by turn_ended -- the
 * one event that always closes out a turn, human or computer alike (dice_rolled/turn_started
 * can't be used for this instead; see the GameState.activePlayerId doc comment on why computer
 * turns never get a turn_started). Mainly useful for computer players' turns, which otherwise fly
 * by with nothing prompting a human to read the full event log to see what happened.
 */
export function summarizeCompletedTurns(log: GameEvent[], board: BoardResponse): TurnSummary[] {
  const summaries: TurnSummary[] = [];
  let actions: string[] = [];

  for (const event of log) {
    if (event.type === "turn_started") {
      // A fresh turn_started with nothing having closed out the last buffer means those events
      // never got a matching turn_ended (shouldn't happen, but events are external input) --
      // drop them rather than misattribute them to the wrong turn.
      actions = [];
      continue;
    }
    if (event.type === "turn_ended") {
      summaries.push({
        turnNumber: event.turnNumber,
        playerId: event.playerId,
        headline: actions.length > 0 ? `${capitalize(actions.join(", "))}.` : "Passed.",
      });
      actions = [];
      continue;
    }
    const description = describeTurnAction(event, board);
    if (description) actions.push(description);
  }

  return summaries;
}

/** Turns one logged event into a human-readable line. playerLabel lets the caller show a
 * username/seat number instead of a raw player id. */
export function describeEvent(
  event: GameEvent,
  board: BoardResponse,
  playerLabel: (playerId: string) => string,
): string {
  switch (event.type) {
    case "connected":
      return "Connected to the game.";
    case "player_ready":
      return `${playerLabel(event.playerId)} is ready.`;
    case "game_started":
      return `Game started! Turn order: ${event.turnOrder.map(playerLabel).join(", ")}.`;
    case "dice_rolled":
      return `${playerLabel(event.playerId)} rolled a ${event.roll}.`;
    case "player_moved":
      return `${playerLabel(event.playerId)} moved to ${spaceLabel(board, event.toSpaceId)}.`;
    case "suit_picked_up":
      return `${playerLabel(event.playerId)} picked up the ${event.suit} suit.`;
    case "promoted":
      return `${playerLabel(event.playerId)} was promoted at ${spaceLabel(board, event.spaceId)}! +${event.goldAwarded} gold.`;
    case "choice_required":
      return `${playerLabel(event.playerId)} needs to choose a path at ${spaceLabel(board, event.spaceId)}.`;
    case "shop_purchase_available":
      return `${playerLabel(event.playerId)} can buy ${spaceLabel(board, event.spaceId)} for ${event.price} gold.`;
    case "shop_purchased":
      return `${playerLabel(event.playerId)} bought ${spaceLabel(board, event.spaceId)} for ${event.price} gold.`;
    case "district_values_recalculated":
      return `Shop values recalculated for a district.`;
    case "stock_trading_available":
      return `${playerLabel(event.playerId)} can trade stock at ${spaceLabel(board, event.spaceId)}.`;
    case "stock_purchased":
      return `${playerLabel(event.playerId)} bought ${event.quantity} share(s) for ${event.totalCost} gold.`;
    case "stock_sold":
      return `${playerLabel(event.playerId)} sold ${event.quantity} share(s) for ${event.totalProceeds} gold.`;
    case "turn_ended":
      return `${playerLabel(event.playerId)}'s turn ended.`;
    case "turn_started":
      return `It's ${playerLabel(event.playerId)}'s turn.`;
    case "game_over":
      return `Game over after ${event.turnCount} turn(s).`;
    case "error":
      return `Error: ${event.message}`;
    case "game_state":
      return "Synced current game state.";
    default:
      return "";
  }
}
