import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../api/client";
import type { BoardResponse, GameResponse, PlayerResponse, UserResponse } from "../api/types";
import { Alert } from "../components/Alert";
import { BoardGraph, type PlayerToken } from "../components/BoardGraph";
import type { StockTradeOfferPayload } from "../api/gameProtocol";
import {
  describeEvent,
  netWorth,
  spaceLabel,
  summarizeCompletedTurns,
  type GameState,
  type PendingPrompt,
} from "../game/gameState";
import { useGameSocket, type ConnectionStatus } from "../game/useGameSocket";
import { playerColor } from "../utils/playerColors";

export function GamePlayPage() {
  const { gameId, playerId } = useParams<{ gameId: string; playerId: string }>();
  const [game, setGame] = useState<GameResponse | null>(null);
  const [board, setBoard] = useState<BoardResponse | null>(null);
  const [players, setPlayers] = useState<PlayerResponse[] | null>(null);
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!gameId) return;
    let cancelled = false;
    setLoading(true);
    setError(null);

    api
      .getGame(gameId)
      .then((gameResult) =>
        Promise.all([
          Promise.resolve(gameResult),
          api.getBoard(gameResult.boardId),
          api.getPlayers(gameId),
          // Best-effort: only used to turn a human player's userId into a username for display.
          api.listUsers(0, 200, "ASC").catch(() => ({ items: [] as UserResponse[] })),
        ]),
      )
      .then(([gameResult, boardResult, playersResult, usersPage]) => {
        if (cancelled) return;
        setGame(gameResult);
        setBoard(boardResult);
        setPlayers(playersResult);
        setUsers(usersPage.items);
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : "Something went wrong.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [gameId]);

  if (!gameId || !playerId) return null;
  if (loading) return <div className="page">Loading…</div>;
  if (error) {
    return (
      <div className="page">
        <Alert kind="error">{error}</Alert>
      </div>
    );
  }
  if (!game || !board || !players) return null;

  if (!players.some((p) => p.id === playerId)) {
    return (
      <div className="page">
        <Alert kind="error">
          {playerId} isn't a player in this game. <Link to={`/games/${gameId}`}>Back to the game</Link>
        </Alert>
      </div>
    );
  }

  return (
    <ConnectedGame
      gameId={gameId}
      playerId={playerId}
      game={game}
      board={board}
      players={players}
      users={users}
    />
  );
}

function ConnectedGame({
  gameId,
  playerId,
  game,
  board,
  players,
  users,
}: {
  gameId: string;
  playerId: string;
  game: GameResponse;
  board: BoardResponse;
  players: PlayerResponse[];
  users: UserResponse[];
}) {
  const playerIds = useMemo(() => players.map((p) => p.id), [players]);
  const { state, status, closeReason, send } = useGameSocket(gameId, playerId, board, playerIds);

  // Stable per-player color/name, independent of turn order (which isn't known until
  // game_started) -- keyed off the players list's own order instead.
  const usernameById = useMemo(() => new Map(users.map((u) => [u.id, u.username])), [users]);
  const colorById = useMemo(() => {
    const map = new Map<string, string>();
    players.forEach((p, index) => map.set(p.id, playerColor(index)));
    return map;
  }, [players]);
  const displayNameById = useMemo(() => {
    const map = new Map<string, string>();
    let computerCount = 0;
    for (const p of players) {
      if (p.userId) {
        map.set(p.id, usernameById.get(p.userId) ?? `User ${p.userId.slice(0, 8)}`);
      } else {
        computerCount += 1;
        map.set(p.id, `Computer ${computerCount}`);
      }
    }
    return map;
  }, [players, usernameById]);

  function playerLabel(id: string): string {
    const name = displayNameById.get(id) ?? id;
    return id === playerId ? `${name} (you)` : name;
  }

  const tokensBySpaceId = useMemo(() => {
    const map: Record<string, PlayerToken[]> = {};
    for (const player of Object.values(state.players)) {
      if (!player.currentSpaceId) continue;
      const token: PlayerToken = {
        id: player.id,
        label: playerLabel(player.id),
        color: colorById.get(player.id) ?? "#8a8f98",
      };
      (map[player.currentSpaceId] ??= []).push(token);
    }
    return map;
  }, [state.players, colorById]);

  const myPlayer = state.players[playerId];
  const isMyTurn = state.phase === "in_progress" && state.activePlayerId === playerId;
  const canRoll = isMyTurn && state.pendingPrompt === null && status === "open";

  // Computer players act on their own the instant it's their turn -- nothing pauses for a human
  // to watch, so without this a computer's turn would just be a blur of activity log lines. This
  // reduces each of their completed turns down to one line: what they rolled, where they ended
  // up, and anything they bought along the way.
  const computerPlayerIds = useMemo(
    () => new Set(players.filter((p) => !p.userId).map((p) => p.id)),
    [players],
  );
  const computerTurnSummaries = useMemo(
    () => summarizeCompletedTurns(state.log, board).filter((turn) => computerPlayerIds.has(turn.playerId)),
    [state.log, board, computerPlayerIds],
  );

  return (
    <div className="page">
      <div className="page__header">
        <h1>{board.name}</h1>
        <div className="section__actions">
          <ConnectionBadge status={status} closeReason={closeReason} />
          <Link to={`/games/${gameId}`} className="button">
            Game details
          </Link>
        </div>
      </div>

      {state.lastError && <Alert kind="error">{state.lastError}</Alert>}

      <div className="grid grid--3">
        <div className="card">
          <h3>Phase</h3>
          <p className="stat stat--small">{phaseLabel(state.phase)}</p>
        </div>
        <div className="card">
          <h3>Turn</h3>
          <p className="stat stat--small">
            {state.phase === "in_progress" && state.activePlayerId
              ? `#${state.turnNumber} — ${playerLabel(state.activePlayerId)}`
              : "—"}
          </p>
          {state.lastRoll && state.lastRoll.playerId === state.activePlayerId && (
            <p className="hint">Rolled a {state.lastRoll.roll}</p>
          )}
        </div>
        <div className="card">
          <h3>Target net worth</h3>
          <p className="stat">{game.targetNetWorth}</p>
        </div>
      </div>

      {state.phase === "waiting_for_ready" && (
        <section className="card">
          <h2>Waiting for players</h2>
          <ul className="ready-list">
            {players.map((p) => (
              <li key={p.id} className="ready-list__item">
                <span
                  className="board-graph__token"
                  style={{ backgroundColor: colorById.get(p.id) }}
                >
                  {playerLabel(p.id).slice(0, 2).toUpperCase()}
                </span>
                {playerLabel(p.id)}
                <span className={`ready-list__status${state.players[p.id]?.ready ? " ready-list__status--ready" : ""}`}>
                  {state.players[p.id]?.ready ? "Ready" : "Not ready"}
                </span>
              </li>
            ))}
          </ul>
          <button
            type="button"
            className="button button--primary"
            disabled={status !== "open" || Boolean(myPlayer?.ready)}
            onClick={() => send({ type: "ready" })}
          >
            {myPlayer?.ready ? "Waiting for everyone else…" : "Ready up"}
          </button>
        </section>
      )}

      {state.phase !== "waiting_for_ready" && (
        <section className="card">
          <div className="section__header">
            <h2>Your turn</h2>
            {canRoll && (
              <button type="button" className="button button--primary" onClick={() => send({ type: "roll_dice" })}>
                Roll dice
              </button>
            )}
          </div>
          {!canRoll && state.phase === "in_progress" && !state.pendingPrompt && (
            <p className="hint">
              {isMyTurn
                ? status === "open"
                  ? "Rolling…"
                  : "Not connected."
                : state.activePlayerId
                  ? `Waiting for ${playerLabel(state.activePlayerId)} to roll.`
                  : "Waiting for the game to start."}
            </p>
          )}
          {state.pendingPrompt && (
            <PendingPromptPanel
              prompt={state.pendingPrompt}
              board={board}
              myPlayerId={playerId}
              myGold={myPlayer?.gold ?? 0}
              playerLabel={playerLabel}
              send={send}
            />
          )}
        </section>
      )}

      {computerTurnSummaries.length > 0 && (
        <section className="card">
          <h2>Computer turns</h2>
          <ul className="event-log">
            {[...computerTurnSummaries]
              .slice(-10)
              .reverse()
              .map((turn) => (
                <li key={turn.turnNumber}>
                  <strong>{playerLabel(turn.playerId)}</strong> (turn #{turn.turnNumber}): {turn.headline}
                </li>
              ))}
          </ul>
        </section>
      )}

      <section className="card">
        <h2>Board</h2>
        <BoardGraph board={board} tokensBySpaceId={tokensBySpaceId} />
      </section>

      <section className="card">
        <h2>Players</h2>
        <table className="table">
          <thead>
            <tr>
              <th>Player</th>
              <th>Gold</th>
              <th>Net worth</th>
              <th>Suits</th>
              <th>Promotions</th>
            </tr>
          </thead>
          <tbody>
            {[...players]
              .sort((a, b) => netWorth(state, b.id) - netWorth(state, a.id))
              .map((p) => {
                const playerState = state.players[p.id];
                return (
                  <tr key={p.id} className={p.id === state.activePlayerId ? "table__row--active" : undefined}>
                    <td>
                      <span
                        className="board-graph__token"
                        style={{ backgroundColor: colorById.get(p.id) }}
                      >
                        {playerLabel(p.id).slice(0, 2).toUpperCase()}
                      </span>{" "}
                      {playerLabel(p.id)}
                    </td>
                    <td>{playerState?.gold ?? "—"}</td>
                    <td>{netWorth(state, p.id)}</td>
                    <td>{playerState?.heldSuits.join(", ") || "—"}</td>
                    <td>{playerState?.promotionCount ?? 0}</td>
                  </tr>
                );
              })}
          </tbody>
        </table>
      </section>

      <section className="card">
        <h2>Activity</h2>
        {state.log.length === 0 ? (
          <p>Nothing has happened yet.</p>
        ) : (
          <ul className="event-log">
            {[...state.log]
              .slice(-30)
              .reverse()
              .map((event, index) => (
                <li key={index}>{describeEvent(event, board, playerLabel)}</li>
              ))}
          </ul>
        )}
      </section>
    </div>
  );
}

function phaseLabel(phase: GameState["phase"]): string {
  switch (phase) {
    case "waiting_for_ready":
      return "Waiting for ready";
    case "in_progress":
      return "In progress";
    case "game_over":
      return "Game over";
  }
}

function ConnectionBadge({
  status,
  closeReason,
}: {
  status: ConnectionStatus;
  closeReason: string | null;
}) {
  if (status === "open") return <span className="status status--ok">Live</span>;
  if (status === "connecting") return <span className="status status--loading">Connecting…</span>;
  return <span className="status status--error">Disconnected{closeReason ? `: ${closeReason}` : ""}</span>;
}

function PendingPromptPanel({
  prompt,
  board,
  myPlayerId,
  myGold,
  playerLabel,
  send,
}: {
  prompt: PendingPrompt;
  board: BoardResponse;
  myPlayerId: string;
  myGold: number;
  playerLabel: (id: string) => string;
  send: ReturnType<typeof useGameSocket>["send"];
}) {
  const isMine = prompt.playerId === myPlayerId;

  if (!isMine) {
    return <p className="hint">Waiting for {playerLabel(prompt.playerId)} to decide…</p>;
  }

  if (prompt.kind === "choose_path") {
    return (
      <div className="prompt-panel">
        <p>Choose a path from {spaceLabel(board, prompt.spaceId)}:</p>
        <div className="section__actions">
          {prompt.options.map((option) => (
            <button
              key={option.toSpaceId}
              type="button"
              className="button"
              onClick={() => send({ type: "choose_path", spaceId: option.toSpaceId })}
            >
              Branch {option.branchOrder} → {spaceLabel(board, option.toSpaceId)}
            </button>
          ))}
        </div>
      </div>
    );
  }

  if (prompt.kind === "shop_purchase") {
    const canAfford = myGold >= prompt.price;
    return (
      <div className="prompt-panel">
        <p>
          Buy {spaceLabel(board, prompt.spaceId)} for {prompt.price} gold?{" "}
          {!canAfford && <span className="field__error">You only have {myGold} gold.</span>}
        </p>
        <div className="section__actions">
          <button
            type="button"
            className="button button--primary"
            disabled={!canAfford}
            onClick={() => send({ type: "buy_shop" })}
          >
            Buy
          </button>
          <button type="button" className="button" onClick={() => send({ type: "decline_shop" })}>
            Decline
          </button>
        </div>
      </div>
    );
  }

  return (
    <StockTradePanel
      spaceId={prompt.spaceId}
      board={board}
      offers={prompt.offers}
      myGold={myGold}
      send={send}
    />
  );
}

function StockTradePanel({
  spaceId,
  board,
  offers,
  myGold,
  send,
}: {
  spaceId: string;
  board: BoardResponse;
  offers: StockTradeOfferPayload[];
  myGold: number;
  send: ReturnType<typeof useGameSocket>["send"];
}) {
  const [quantities, setQuantities] = useState<Record<string, string>>({});

  function quantityFor(districtId: string): number {
    const parsed = Number(quantities[districtId]);
    return Number.isInteger(parsed) && parsed >= 1 && parsed <= 99 ? parsed : 1;
  }

  const districtNameById = new Map(board.districts.map((d) => [d.id, d.name]));

  return (
    <div className="prompt-panel">
      <p>Trade stock at {spaceLabel(board, spaceId)}:</p>
      <table className="table">
        <thead>
          <tr>
            <th>District</th>
            <th>Price/share</th>
            <th>Owned</th>
            <th>Quantity</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {offers.map((offer) => {
            const quantity = quantityFor(offer.districtId);
            const canAfford = myGold >= quantity * offer.pricePerShare;
            return (
              <tr key={offer.districtId}>
                <td>{districtNameById.get(offer.districtId) ?? offer.districtId}</td>
                <td>{offer.pricePerShare}</td>
                <td>{offer.ownedQuantity}</td>
                <td>
                  <input
                    type="text"
                    inputMode="numeric"
                    value={quantities[offer.districtId] ?? "1"}
                    onChange={(e) =>
                      setQuantities((q) => ({ ...q, [offer.districtId]: e.target.value }))
                    }
                  />
                </td>
                <td>
                  <div className="section__actions">
                    <button
                      type="button"
                      className="button button--small"
                      disabled={!canAfford}
                      onClick={() =>
                        send({ type: "buy_stock", districtId: offer.districtId, quantity })
                      }
                    >
                      Buy
                    </button>
                    <button
                      type="button"
                      className="button button--small"
                      disabled={offer.ownedQuantity < quantity}
                      onClick={() =>
                        send({ type: "sell_stock", districtId: offer.districtId, quantity })
                      }
                    >
                      Sell
                    </button>
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      <button type="button" className="button" onClick={() => send({ type: "skip_stock_trade" })}>
        Skip trading
      </button>
    </div>
  );
}
