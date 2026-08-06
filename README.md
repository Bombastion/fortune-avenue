# Fortune Avenue

A web-based recreation of [Fortune Street](https://en.wikipedia.org/wiki/Fortune_Street), the board game video game that combines Mario-style board gameplay with a stock market/property-trading layer. Fortune Avenue rebuilds it as something playable in the browser.

See [`service/README.md`](service/README.md) for details on how the server works and instructions for common development tasks.

## Technical details for nerds

### Client

Pending — not started yet. Planning on a React frontend hosted somewhere.

### Server

The server lives in [`service/`](service/) and is a Kotlin + Spring Boot application. It exposes a WebSocket endpoint for real-time gameplay and a small REST API for everything else, backed by a Postgres database. The whole stack runs in Docker via Docker Compose.

See the [server README](service/README.md) for details about how the backend works if you're interested.
