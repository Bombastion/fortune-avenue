import { useEffect, useState } from "react";

// Simplest possible integration with the backend: hit the health check
// endpoint on load and show whether the API is reachable. This is meant
// as a starting point/sanity check that the client, reverse proxy, and
// backend are all wired together correctly -- real gameplay UI comes later.
type HealthState = "loading" | "ok" | "error";

function App() {
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
    <main className="app">
      <h1>Fortune Avenue</h1>
      <p>
        Backend status: <span className={`status status--${health}`}>{healthLabel(health)}</span>
      </p>
    </main>
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

export default App;
