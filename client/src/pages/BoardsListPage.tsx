import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api/client";
import type { BoardResponse, SortDirection } from "../api/types";
import { Alert } from "../components/Alert";

const PAGE_SIZE = 10;

export function BoardsListPage() {
  const [page, setPage] = useState(0);
  const [direction, setDirection] = useState<SortDirection>("ASC");
  const [boards, setBoards] = useState<BoardResponse[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    api
      .listBoards(page, PAGE_SIZE, direction)
      .then((result) => {
        if (cancelled) return;
        setBoards(result.items);
        setTotalPages(result.totalPages);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(err instanceof Error ? err.message : "Something went wrong.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [page, direction]);

  return (
    <div className="page">
      <div className="page__header">
        <h1>Boards</h1>
        <Link to="/boards/new" className="button">
          New board
        </Link>
      </div>

      <div className="toolbar">
        <label className="toolbar__control">
          Sort by name
          <select
            value={direction}
            onChange={(event) => {
              setDirection(event.target.value as SortDirection);
              setPage(0);
            }}
          >
            <option value="ASC">A → Z</option>
            <option value="DESC">Z → A</option>
          </select>
        </label>
      </div>

      {error && <Alert kind="error">{error}</Alert>}

      {loading ? (
        <p>Loading…</p>
      ) : boards.length === 0 ? (
        <p>No boards yet. Create one to get started.</p>
      ) : (
        <table className="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Spaces</th>
              <th>Districts</th>
              <th>Starting gold</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {boards.map((board) => (
              <tr key={board.id}>
                <td>{board.name}</td>
                <td>{board.spaces.length}</td>
                <td>{board.districts.length}</td>
                <td>{board.startingGold}</td>
                <td>
                  <Link to={`/boards/${board.id}`}>View</Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {totalPages > 1 && (
        <div className="pagination">
          <button
            type="button"
            className="button button--small"
            disabled={page <= 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            Previous
          </button>
          <span>
            Page {page + 1} of {totalPages}
          </span>
          <button
            type="button"
            className="button button--small"
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}
