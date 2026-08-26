# Fortune Avenue — Client

React + TypeScript (Vite) frontend for Fortune Avenue.

Right now it only calls `GET /health` on the backend and shows whether the API is reachable — a
sanity check that the client, reverse proxy, and backend are wired together, not real gameplay UI
yet.

## Running it

The client is meant to run as part of the full stack — see the [project README](../README.md)
(`make up` from the project root). Once that's running:

| URL | What you get |
| --- | --- |
| http://localhost:8000 | The client, with `/health` (and future API paths) proxied to the backend — this is the "real" way to use the app |
| http://localhost:3000 | The client on its own, for a quick look — API calls from here won't resolve, since there's no proxy in front of it |
| http://localhost:8080 | The backend on its own, unchanged |

## Running just the client via Docker

To build and run only the client container — no backend, Postgres, or proxy — use the Makefile in
this directory. Run `make help` (or just `make`) at any time to see the full list from the
terminal.

| Command | What it does |
| --- | --- |
| `make up` | Build (if needed) and start the client on its own, at http://localhost:3000 |
| `make down` | Stop and remove the client's container |
| `make build` | Build the production static assets in a container, verifying the build stage succeeds, without starting anything |
| `make test` | Build (if needed), run the full test suite, and tear the test stack down afterward |
| `make test-filter TESTS="..."` | Run specific tests only, e.g. `make test-filter TESTS="src/validation/rules.test.ts"` |
| `make down-test` | Manually stop and remove the test stack, if a run was interrupted |

`make up`'s single-container setup is the same as `localhost:3000` above: API calls made against it
won't resolve, since nothing is proxying them to the backend. It's for previewing the built UI in
isolation, not exercising real API calls.

## Running tests

Tests run with [Vitest](https://vitest.dev/) and live next to the code they cover, as `*.test.ts`
files (e.g. `src/validation/rules.test.ts`). `make test` builds a disposable Docker image (a `test`
stage in the Dockerfile, reusing the same cached `npm install` and build steps as the runtime image
— see `docker-compose.test.yml`) and runs the whole suite in it, mirroring how the
[service](../service/README.md) runs its own tests:

```bash
make test                                              # everything
make test-filter TESTS="src/api/json.test.ts"          # one file
make test-filter TESTS="src/validation"                # everything under a path
```

No backend or database is involved — today's tests only cover pure logic (form validation, the
board-graph reachability check, request serialization), so there's nothing to spin up beyond the
client image itself.

To run tests directly on your machine instead (faster feedback while iterating), see "Local
development without Docker" below, then run `npm test` (or `npx vitest` for watch mode).

## Local development without Docker

For faster iteration on the UI, you can also run the dev server directly on your machine (requires
Node 22+):

```bash
npm install
npm run dev
```

This starts Vite's dev server (with hot reload) on its own port, printed in the terminal. API calls
made this way will fail the same way they do against `localhost:3000` above, since nothing is
proxying them to the backend — run against `localhost:8000` (via `make up`) to exercise real API
calls.

There's no `package-lock.json` committed yet since it wasn't generated from a real `npm install` run.
Running `npm install` locally will create one — worth committing once it exists, so Docker builds
become reproducible (`npm ci` instead of `npm install`).
