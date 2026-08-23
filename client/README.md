# Fortune Avenue — Client

React + TypeScript (Vite) frontend for Fortune Avenue.

Right now it only calls `GET /health` on the backend and shows whether the API is reachable — a
sanity check that the client, reverse proxy, and backend are wired together, not real gameplay UI
yet.

## Running it

The client is meant to run as part of the full stack — see [`service/README.md`](../service/README.md)
(`make up` from `service/`). Once that's running:

| URL | What you get |
| --- | --- |
| http://localhost:8000 | The client, with `/health` (and future API paths) proxied to the backend — this is the "real" way to use the app |
| http://localhost:3000 | The client on its own, for a quick look — API calls from here won't resolve, since there's no proxy in front of it |
| http://localhost:8080 | The backend on its own, unchanged |

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
