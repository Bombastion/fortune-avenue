import { type FormEvent, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../api/client";
import type { GameResponse, PlayerResponse } from "../api/types";
import { Alert, ErrorSummary } from "../components/Alert";
import { Field } from "../components/Field";
import { isBlank } from "../validation/rules";

export function GameDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [game, setGame] = useState<GameResponse | null>(null);
  const [players, setPlayers] = useState<PlayerResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    setLoading(true);
    setError(null);

    Promise.all([api.getGame(id), api.getPlayers(id)])
      .then(([gameResult, playersResult]) => {
        if (cancelled) return;
        setGame(gameResult);
        setPlayers(playersResult);
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
  }, [id, reloadToken]);

  if (loading) return <div className="page">Loading…</div>;
  if (error) {
    return (
      <div className="page">
        <Alert kind="error">{error}</Alert>
      </div>
    );
  }
  if (!game || !id) return null;

  return (
    <div className="page">
      <h1>Game</h1>
      <div className="grid grid--3">
        <div className="card">
          <h3>Game id</h3>
          <p className="stat stat--small">
            <code>{game.id}</code>
          </p>
        </div>
        <div className="card">
          <h3>Board</h3>
          <p className="stat stat--small">
            <Link to={`/boards/${game.boardId}`}>
              <code>{game.boardId}</code>
            </Link>
          </p>
        </div>
        <div className="card">
          <h3>Target net worth</h3>
          <p className="stat">{game.targetNetWorth}</p>
        </div>
      </div>

      <section className="card">
        <h2>Players ({players.length})</h2>
        {players.length === 0 ? (
          <p>No players yet.</p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Player id</th>
                <th>Type</th>
              </tr>
            </thead>
            <tbody>
              {players.map((player) => (
                <tr key={player.id}>
                  <td>
                    <code>{player.id}</code>
                  </td>
                  <td>{player.userId ? <code>{player.userId}</code> : "Computer opponent"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <AddPlayerForm gameId={id} onPlayerAdded={() => setReloadToken((t) => t + 1)} />
    </div>
  );
}

function AddPlayerForm({ gameId, onPlayerAdded }: { gameId: string; onPlayerAdded: () => void }) {
  const [playerKind, setPlayerKind] = useState<"human" | "computer">("computer");
  const [userId, setUserId] = useState("");
  const [errors, setErrors] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);

  function validate(): string[] {
    if (playerKind === "human" && isBlank(userId)) {
      return ["Enter the user id of the player to add."];
    }
    return [];
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();

    const problems = validate();
    setErrors(problems);
    if (problems.length > 0) return;

    setSubmitting(true);
    try {
      await api.addPlayer(gameId, {
        userId: playerKind === "human" ? userId.trim() : undefined,
      });
      setUserId("");
      onPlayerAdded();
    } catch (err) {
      setErrors([err instanceof Error ? err.message : "Something went wrong."]);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="card" onSubmit={handleSubmit} noValidate>
      <h2>Add a player</h2>
      <ErrorSummary errors={errors} />

      <div className="radio-group">
        <label className="radio">
          <input
            type="radio"
            name="playerKind"
            checked={playerKind === "computer"}
            onChange={() => setPlayerKind("computer")}
          />
          Computer opponent
        </label>
        <label className="radio">
          <input
            type="radio"
            name="playerKind"
            checked={playerKind === "human"}
            onChange={() => setPlayerKind("human")}
          />
          Human player
        </label>
      </div>

      {playerKind === "human" && (
        <Field label="User id" hint={<>Find or create one on the <Link to="/users">Users</Link> page</>}>
          <input
            type="text"
            value={userId}
            onChange={(e) => setUserId(e.target.value)}
            placeholder="UUID"
          />
        </Field>
      )}

      <button type="submit" className="button button--primary" disabled={submitting}>
        {submitting ? "Adding…" : "Add player"}
      </button>
    </form>
  );
}
