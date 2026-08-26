import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

type HealthState = "loading" | "ok" | "error";

export function HomePage() {
  const [health, setHealth] = useState<HealthState>("loading");

  useEffect(() => {
    let cancelled = false;

    fetch("/health")
      .then((response) => {
        if (!response.ok) throw new Error(`Unexpected status ${response.status}`);
        return response.json() as Promise<{ status: string }>;
      })
      .then((data) => {
        if (!cancelled) setHealth(data.status === "ok" ? "ok" : "error");
      })
      .catch(() => {
        if (!cancelled) setHealth("error");
      });

    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="page">
      <h1>Fortune Avenue</h1>
      <p>
        Backend status: <span className={`status status--${health}`}>{healthLabel(health)}</span>
      </p>
      <div className="card">
        <h2>Get started</h2>
        <p>
          Create a <Link to="/users">user</Link>, design a <Link to="/boards/new">board</Link>, and
          start a <Link to="/games">game</Link> on it.
        </p>
      </div>
    </div>
  );
}

function healthLabel(health: HealthState): string {
  switch (health) {
    case "loading":
      return "checking…";
    case "ok":
      return "ok";
    case "error":
      return "unreachable";
  }
}
