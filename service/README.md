# Fortune Avenue — Server

Kotlin + Spring Boot backend for Fortune Avenue.

## How it works

- **WebSocket** (`/ws/game`) carries real-time game state — dice rolls, turns, board updates — since players need to see each other's moves live rather than polling for them.
- **REST** handles everything that isn't part of a live game session: health checks (`/health`), users (`/users`), boards (`/boards`), games (`/games`, tied to a board), and players (`/games/{gameId}/players`, optionally tied to a user) today, with room for auth, lobbies, and history later.
- **Persistence** uses [Exposed](https://github.com/JetBrains/Exposed) as the ORM against Postgres, with [Flyway](https://flywaydb.org/) managing schema migrations (`src/main/resources/db/migration/`).
- **Docker**: `docker-compose.yml` here runs just this app and its Postgres instance — `make up`/`make down` in this directory affect the backend only. The [project root](..) has its own `docker-compose.yml` and `Makefile` that run the whole stack — this app, the [client](../client/README.md), Postgres, and an nginx reverse proxy in front of the first two. A separate `docker-compose.test.yml` here runs the test suite against its own, disposable Postgres instance, so tests never touch the app's data. Every compose file sets an explicit `name:` (this one is `fortune-avenue-service`), so containers, networks, and volumes are always prefixed with the project — e.g. the Postgres data volume is `fortune-avenue-service_postgres-data`, not a bare `service_postgres-data`.
- **Formatting**: [ktfmt](https://github.com/facebook/ktfmt) (kotlinlang style) enforces a consistent Kotlin style. `./gradlew check` runs `ktfmtCheck` automatically; `make fmt` / `make lint` are shortcuts that don't require a local JDK.

## Common actions

All commands below run from inside this directory (`service/`) and assume Docker is running. Run `make help` (or just `make`) at any time to see this list from the terminal.

| Command | What it does |
| --- | --- |
| `make up` | Build (if needed) and start the backend + its Postgres instance (no client/proxy) |
| `make down` | Stop and remove the backend's containers |
| `make test` | Build (if needed), run the full test suite, and tear the test stack down afterward |
| `make test-filter TESTS="..."` | Run specific tests only, e.g. `make test-filter TESTS="com.fortuneavenue.server.rest.UserControllerTest"` |
| `make down-test` | Manually stop and remove the test stack, if a run was interrupted |
| `make fmt` | Reformat Kotlin sources with [ktfmt](https://github.com/facebook/ktfmt), writing changes to disk |
| `make lint` | Check Kotlin formatting without modifying files — same check `./gradlew check` runs |

Once `make up` is running:

| URL | What's there |
| --- | --- |
| `localhost:8080` | The backend — `GET /health` is a good first check |

To bring up the client and reverse proxy too, use `make up` from the [project root](..) instead — that starts the full stack, with the client + backend together behind the proxy at `localhost:8000` (see the [project README](../README.md)).
