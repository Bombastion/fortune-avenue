import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../api/client";
import type { BoardResponse } from "../api/types";
import { Alert } from "../components/Alert";

export function BoardDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [board, setBoard] = useState<BoardResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    setLoading(true);
    setError(null);

    api
      .getBoard(id)
      .then((result) => {
        if (!cancelled) setBoard(result);
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
  }, [id]);

  if (loading) return <div className="page">Loading…</div>;
  if (error) {
    return (
      <div className="page">
        <Alert kind="error">{error}</Alert>
      </div>
    );
  }
  if (!board) return null;

  const districtNameById = new Map(board.districts.map((d) => [d.id, d.name]));

  return (
    <div className="page">
      <div className="page__header">
        <h1>{board.name}</h1>
        <Link to="/games" className="button button--primary">
          Start a game on this board
        </Link>
      </div>

      <div className="grid grid--3">
        <div className="card">
          <h3>Starting gold</h3>
          <p className="stat">{board.startingGold}</p>
        </div>
        <div className="card">
          <h3>Base salary</h3>
          <p className="stat">{board.baseSalary}</p>
        </div>
        <div className="card">
          <h3>Promotion bonus</h3>
          <p className="stat">{board.promotionBonus}</p>
        </div>
      </div>

      <section className="card">
        <h2>Spaces ({board.spaces.length})</h2>
        <table className="table">
          <thead>
            <tr>
              <th>Type</th>
              <th>Base value</th>
              <th>Base price %</th>
              <th>District</th>
              <th>Start space</th>
            </tr>
          </thead>
          <tbody>
            {board.spaces.map((space) => (
              <tr key={space.id}>
                <td>{space.spaceType}</td>
                <td>{space.baseValue ?? "—"}</td>
                <td>{space.basePricePercentage ?? "—"}</td>
                <td>{space.districtId ? (districtNameById.get(space.districtId) ?? space.districtId) : "—"}</td>
                <td>{space.id === board.startSpaceId ? "✓" : ""}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="card">
        <h2>Paths ({board.paths.length})</h2>
        <table className="table">
          <thead>
            <tr>
              <th>From</th>
              <th>To</th>
              <th>Branch order</th>
            </tr>
          </thead>
          <tbody>
            {board.paths.map((path, index) => (
              <tr key={`${path.from}-${path.to}-${path.branchOrder}-${index}`}>
                <td>{path.from}</td>
                <td>{path.to}</td>
                <td>{path.branchOrder}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      {board.districts.length > 0 && (
        <section className="card">
          <h2>Districts ({board.districts.length})</h2>
          {board.districts.map((district) => (
            <div key={district.id} className="district-summary">
              <h3>
                <span
                  className="color-swatch"
                  style={{ backgroundColor: `#${district.colorHex}` }}
                  aria-hidden="true"
                />
                {district.name}
              </h3>
              <p>Minimum stock percentage: {district.minimumStockPercentage}</p>
              {district.progressions.length > 0 && (
                <table className="table">
                  <thead>
                    <tr>
                      <th>Owned shop count</th>
                      <th>Existing shop boost %</th>
                      <th>New shop boost %</th>
                    </tr>
                  </thead>
                  <tbody>
                    {district.progressions.map((p) => (
                      <tr key={p.ownedShopCount}>
                        <td>{p.ownedShopCount}</td>
                        <td>{p.existingShopBoostPercentage}</td>
                        <td>{p.newShopBoostPercentage}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          ))}
        </section>
      )}
    </div>
  );
}
