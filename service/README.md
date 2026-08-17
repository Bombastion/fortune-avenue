# Fortune Avenue — Server

Kotlin + Spring Boot backend for Fortune Avenue.

## How it works

- **WebSocket** (`/ws/game`) carries real-time game state — dice rolls, turns, board updates — since players need to see each other's moves live rather than polling for them.
- **REST** handles everything that isn't part of a live game session: health checks (`/health`), users (`/users`), boards (`/boards`), games (`/games`, tied to a board), and players (`/games/{gameId}/players`, optionally tied to a user) today, with room for auth, lobbies, and history later.
- **Persistence** uses [Exposed](https://github.com/JetBrains/Exposed) as the ORM against Postgres, with [Flyway](https://flywaydb.org/) managing schema migrations (`src/main/resources/db/migration/`).
- **Docker**: the app and its Postgres instance run via `docker-compose.yml`. A separate `docker-compose.test.yml` runs the test suite against its own, disposable Postgres instance, so tests never touch the app's data.
- **Formatting**: [ktfmt](https://github.com/facebook/ktfmt) (kotlinlang style) enforces a consistent Kotlin style. `./gradlew check` runs `ktfmtCheck` automatically; `make fmt` / `make lint` are shortcuts that don't require a local JDK.

## Common actions

All commands below run from inside this directory (`service/`) and assume Docker is running. Run `make help` (or just `make`) at any time to see this list from the terminal.

| Command | What it does |
| --- | --- |
| `make up` | Build (if needed) and start the app + its Postgres instance |
| `make down` | Stop and remove the app's containers |
| `make test` | Build (if needed), run the full test suite, and tear the test stack down afterward |
| `make test-filter TESTS="..."` | Run specific tests only, e.g. `make test-filter TESTS="com.fortuneavenue.server.rest.UserControllerTest"` |
| `make down-test` | Manually stop and remove the test stack, if a run was interrupted |
| `make fmt` | Reformat Kotlin sources with [ktfmt](https://github.com/facebook/ktfmt), writing changes to disk |
| `make lint` | Check Kotlin formatting without modifying files — same check `./gradlew check` runs |

Once `make up` is running, the app listens on `localhost:8080` — `GET /health` is a good first check.
