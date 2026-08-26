import { type FormEvent, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError, api } from "../api/client";
import type { BoardResponse } from "../api/types";
import { Alert, ErrorSummary } from "../components/Alert";
import { Field } from "../components/Field";
import { isBlank, isPositiveIntegerString } from "../validation/rules";

const BOARD_OPTIONS_PAGE_SIZE = 100;

export function GamesPage() {
  return (
    <div className="page">
      <h1>Games</h1>
      <div className="grid grid--2">
        <CreateGameForm />
        <LookupGameForm />
      </div>
    </div>
  );
}

function CreateGameForm() {
  const navigate = useNavigate();
  const [boards, setBoards] = useState<BoardResponse[]>([]);
  const [boardsError, setBoardsError] = useState<string | null>(null);
  const [boardId, setBoardId] = useState("");
  const [manualBoardId, setManualBoardId] = useState("");
  const [useCustomTarget, setUseCustomTarget] = useState(false);
  const [targetNetWorth, setTargetNetWorth] = useState("6000");
  const [errors, setErrors] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    api
      .listBoards(0, BOARD_OPTIONS_PAGE_SIZE, "ASC")
      .then((result) => {
        if (!cancelled) setBoards(result.items);
      })
      .catch((err) => {
        if (!cancelled) {
          setBoardsError(err instanceof Error ? err.message : "Could not load boards.");
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  function validate(): string[] {
    const problems: string[] = [];
    const effectiveBoardId = boardId || manualBoardId;
    if (isBlank(effectiveBoardId)) problems.push("Choose a board, or enter a board id.");
    if (useCustomTarget && !isPositiveIntegerString(targetNetWorth)) {
      problems.push("Target net worth must be a positive whole number.");
    }
    return problems;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();

    const problems = validate();
    setErrors(problems);
    if (problems.length > 0) return;

    setSubmitting(true);
    try {
      const game = await api.createGame({
        boardId: (boardId || manualBoardId).trim(),
        targetNetWorth: useCustomTarget ? Number(targetNetWorth) : undefined,
      });
      navigate(`/games/${game.id}`);
    } catch (err) {
      setErrors([err instanceof Error ? err.message : "Something went wrong."]);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="card" onSubmit={handleSubmit} noValidate>
      <h2>Start a game</h2>
      <ErrorSummary errors={errors} />
      {boardsError && <Alert kind="error">{boardsError}</Alert>}

      <Field label="Board">
        <select
          value={boardId}
          onChange={(e) => setBoardId(e.target.value)}
          disabled={boards.length === 0}
        >
          <option value="">
            {boards.length === 0 ? "No boards yet — enter an id below" : "— choose a board —"}
          </option>
          {boards.map((board) => (
            <option key={board.id} value={board.id}>
              {board.name}
            </option>
          ))}
        </select>
      </Field>

      <Field label="Or enter a board id directly" hint="Only needed if the board isn't in the list above">
        <input
          type="text"
          value={manualBoardId}
          onChange={(e) => setManualBoardId(e.target.value)}
          placeholder="UUID"
        />
      </Field>

      <label className="checkbox">
        <input
          type="checkbox"
          checked={useCustomTarget}
          onChange={(e) => setUseCustomTarget(e.target.checked)}
        />
        Set a custom target net worth (defaults to 6000)
      </label>

      {useCustomTarget && (
        <Field label="Target net worth" hint="Positive whole number">
          <input
            type="text"
            inputMode="numeric"
            value={targetNetWorth}
            onChange={(e) => setTargetNetWorth(e.target.value)}
          />
        </Field>
      )}

      <button type="submit" className="button button--primary" disabled={submitting}>
        {submitting ? "Starting…" : "Start game"}
      </button>
    </form>
  );
}

function LookupGameForm() {
  const navigate = useNavigate();
  const [id, setId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();

    if (isBlank(id)) {
      setError("Enter a game id.");
      return;
    }

    setError(null);
    setLoading(true);
    try {
      await api.getGame(id.trim());
      navigate(`/games/${id.trim()}`);
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        setError("No game found with that id.");
      } else {
        setError(err instanceof Error ? err.message : "Something went wrong.");
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <form className="card" onSubmit={handleSubmit} noValidate>
      <h2>Go to an existing game</h2>
      {error && <Alert kind="error">{error}</Alert>}
      <Field label="Game id">
        <input type="text" value={id} onChange={(e) => setId(e.target.value)} placeholder="UUID" />
      </Field>
      <button type="submit" className="button" disabled={loading}>
        {loading ? "Looking up…" : "Go"}
      </button>
    </form>
  );
}
