# Fortune Avenue

A web-based recreation of [Fortune Street](https://en.wikipedia.org/wiki/Fortune_Street), the board game video game that combines Mario-style board gameplay with a stock market/property-trading layer. Fortune Avenue rebuilds it as something playable in the browser.

See [`service/README.md`](service/README.md) for details on how the server works and instructions for common development tasks.

## Technical details for nerds

If you're curious how things are calculated, I found [this incredibly helpful blog post](https://bluepichu.wordpress.com/2012/08/07/fortune-street-calculations-part-1-starting-conditions/) where the author was trying to do basically this project back in 2012.

### Client

Pending — not started yet. Planning on a React frontend hosted somewhere.

### Server

The server lives in [`service/`](service/) and is a Kotlin + Spring Boot application. It exposes a WebSocket endpoint for real-time gameplay and a small REST API for everything else, backed by a Postgres database. The whole stack runs in Docker via Docker Compose.

See the [server README](service/README.md) for details about how the backend works if you're interested.

## Running an example game

This walks through starting the server, setting up a game via REST, and playing it out over the WebSocket endpoint. All commands assume the server is running locally on `localhost:8080` (`make up` from `service/` — see the [server README](service/README.md)).

### 1. Set up the game via REST

Create a board (every space must be reachable from the start space *and* able to path back to it — i.e. a closed loop, not just a line), a game on that board, a user, and a player for that user in the game:

```bash
# Create a board -> save the returned id as BOARD_ID
curl -s -X POST http://localhost:8080/boards \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Board",
    "spaces": [
      { "spaceType": "BASIC" },
      { "spaceType": "BASIC" },
      { "spaceType": "BASIC" }
    ],
    "paths": [
      { "from": 0, "to": 1, "branchOrder": 0 },
      { "from": 1, "to": 2, "branchOrder": 0 },
      { "from": 2, "to": 0, "branchOrder": 0 }
    ],
    "startSpaceIndex": 0
  }'

# Create a game on that board -> save the returned id as GAME_ID
curl -s -X POST http://localhost:8080/games \
  -H "Content-Type: application/json" \
  -d '{ "boardId": "BOARD_ID" }'

# Create a user -> save the returned id as USER_ID
curl -s -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{ "username": "coolgamer" }'

# Add the user as a player in the game -> save the returned id as PLAYER_ID
curl -s -X POST http://localhost:8080/games/GAME_ID/players \
  -H "Content-Type: application/json" \
  -d '{ "userId": "USER_ID" }'
```

`userId` on the player call is optional — omit it (or pass `{}`) for an anonymous player. Repeat the user/player steps to add more players; a game needs at least one player, but `markReady` only starts the game once every player in it has readied up.

### 2. Play the game over WebSocket

Connect one WebSocket per player to `ws://localhost:8080/ws/game?gameId=GAME_ID&playerId=PLAYER_ID` (e.g. via Postman's WebSocket request tab, or `wscat -c "..."`). The connection is rejected immediately if `gameId`/`playerId` are missing, malformed, or `playerId` isn't actually a player in that game — so the REST setup above has to happen first.

On connect, each socket gets:

```json
{"type":"connected","playerId":"..."}
```

From each connected player's socket, send:

```json
{"type":"ready"}
```

Once every player has readied up, all sockets receive a broadcast:

```json
{"type":"player_ready","playerId":"..."}
{"type":"game_started","turnOrder":["...","..."]}
```

Then, from whichever player's socket is next in `turnOrder`, send:

```json
{"type":"roll_dice"}
```

All sockets receive the roll, followed by one `player_moved` broadcast per space that roll covers:

```json
{"type":"dice_rolled","playerId":"...","roll":4}
{"type":"player_moved","turnNumber":0,"playerId":"...","fromSpaceId":null,"toSpaceId":"...","movementPointsRemaining":3}
{"type":"player_moved","turnNumber":0,"playerId":"...","fromSpaceId":"...","toSpaceId":"...","movementPointsRemaining":2}
```

If movement reaches a space with more than one path out of it, it pauses there instead of a `player_moved` broadcast, and lists the options:

```json
{"type":"choice_required","playerId":"...","spaceId":"...","options":[{"toSpaceId":"...","branchOrder":0},{"toSpaceId":"...","branchOrder":1}]}
```

Reply from that same player's socket with the space to move onto, and movement picks back up (pausing again if it hits another branch):

```json
{"type":"choose_path","spaceId":"..."}
```

Once movement is exhausted, all sockets see the turn end, and — once the game hits its max turn count — an additional game-over event:

```json
{"type":"turn_ended","turnNumber":0,"playerId":"..."}
{"type":"game_over","turnCount":10}
```

Computer players (players added without a `userId`) never send any of this themselves — the server rolls and moves them automatically, randomly picking a path any time it hits a branch, and broadcasts the results the same way.

Session state is kept in memory per server instance, so this only works against a single running instance, not a load-balanced setup.
