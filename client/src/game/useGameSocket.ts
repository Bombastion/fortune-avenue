// Owns the live WebSocket connection to /ws/game for one (gameId, playerId) pair. Kept separate
// from gameState.ts's reducer (which knows nothing about sockets) and from GamePlayPage.tsx
// (which just consumes this hook), the same layering BoardCreatePage.tsx/.state.ts use.
import { useCallback, useEffect, useReducer, useRef, useState } from "react";
import type { BoardResponse } from "../api/types";
import type { ClientMessage } from "../api/gameProtocol";
import { parseGameEvent } from "../api/gameProtocol";
import { applyGameEvent, initialGameState, type GameState } from "./gameState";

export type ConnectionStatus = "connecting" | "open" | "closed";

function gameSocketUrl(gameId: string, playerId: string): string {
  // Same-origin, mirroring api/client.ts's plain-relative fetch("/boards", ...) -- see
  // proxy/nginx.conf, which forwards both REST paths and /ws/ to the backend from one origin.
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${protocol}//${window.location.host}/ws/game?gameId=${encodeURIComponent(gameId)}&playerId=${encodeURIComponent(playerId)}`;
}

export interface UseGameSocketResult {
  state: GameState;
  status: ConnectionStatus;
  /** Set from the close frame's reason once the socket has closed -- e.g. the "not a player in
   * this game" rejection GameWebSocketHandler.kt sends on a bad connection. Null while still
   * connecting/open, or if the socket just closed without one. */
  closeReason: string | null;
  send: (message: ClientMessage) => void;
}

/**
 * Connects to /ws/game?gameId=...&playerId=... (see GameWebSocketHandler.kt) and folds every
 * incoming event onto gameState.ts's reducer. Reconnects are deliberately NOT attempted here --
 * that handler's own doc comment notes its session bookkeeping lives in memory on a single
 * instance, so silently reconnecting after a drop would hide a real loss of live state from the
 * user; closeReason surfaces what happened instead so the page can tell them to refresh.
 */
export function useGameSocket(
  gameId: string,
  playerId: string,
  board: BoardResponse,
  playerIds: string[],
): UseGameSocketResult {
  const [state, dispatch] = useReducer(applyGameEvent, undefined, () =>
    initialGameState(board, playerIds),
  );
  const [status, setStatus] = useState<ConnectionStatus>("connecting");
  const [closeReason, setCloseReason] = useState<string | null>(null);
  const socketRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    const socket = new WebSocket(gameSocketUrl(gameId, playerId));
    socketRef.current = socket;
    setStatus("connecting");
    setCloseReason(null);

    socket.onopen = () => setStatus("open");

    socket.onmessage = (event) => {
      const parsed = parseGameEvent(typeof event.data === "string" ? event.data : "");
      if (parsed) dispatch(parsed);
    };

    socket.onclose = (event) => {
      setStatus("closed");
      setCloseReason(event.reason || null);
    };

    // The browser also fires onclose right after onerror for a connection failure, so this just
    // needs to update status -- the close handler above covers the reason.
    socket.onerror = () => setStatus("closed");

    return () => {
      socket.close();
      socketRef.current = null;
    };
    // Deliberately keyed only on (gameId, playerId): board/playerIds just seed the reducer's
    // initial value via useReducer's lazy initializer, which only ever runs once.
  }, [gameId, playerId]);

  const send = useCallback((message: ClientMessage) => {
    const socket = socketRef.current;
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify(message));
    }
  }, []);

  return { state, status, closeReason, send };
}
