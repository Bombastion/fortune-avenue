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
      { "spaceType": "BASIC", "districtIndex": 0 },
      { "spaceType": "SHOP", "baseValue": 300, "basePricePercentage": 0.2500 },
      { "spaceType": "BASIC", "districtIndex": 0 }
    ],
    "paths": [
      { "from": 0, "to": 1, "branchOrder": 0 },
      { "from": 1, "to": 2, "branchOrder": 0 },
      { "from": 2, "to": 0, "branchOrder": 0 }
    ],
    "startSpaceIndex": 0,
    "startingGold": 1500,
    "districts": [
      {
        "name": "Blue District",
        "colorHex": "1E90FF",
        "minimumStockPercentage": 0.5000,
        "progressions": [
          { "ownedShopCount": 2, "existingShopBoostPercentage": 0.1000, "newShopBoostPercentage": 0.1500 }
        ]
      }
    ]
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

A district's `progressions` describe how shop values there scale as a single player accumulates more of them: `existingShopBoostPercentage` is applied to shops the player already owns in the district, and `newShopBoostPercentage` (typically larger, to make up for missing out on earlier boosts) is applied to the one they just bought. Any district with 2 or more spaces needs exactly one entry per `ownedShopCount` from 2 up to its total space count; a district with fewer spaces needs none.

A district's `minimumStockPercentage` is the floor, as a fraction of the average value of its SHOP spaces, that its stock can trade at once a game starts -- a positive decimal strictly between 0 and 1 with exactly 4 digits (e.g. `0.5000` means the stock can never trade below half the district's average shop value). When a game starts, this is copied onto a per-game `game_district_information` row along with the computed `currentStockValue` -- the average `currentValue` of the district's shops at that moment, multiplied by `minimumStockPercentage` -- for every district that actually contains at least one SHOP space.

A board's `startingGold` is how much gold every player in a game on that board starts with -- it's copied onto each player's state the moment they're added to a game (see `POST /games/{gameId}/players` below).

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

Every space a player passes or lands on is also a candidate for a suit pickup -- a board can have HEART, DIAMOND, SPADE, and CLUB spaces (in addition to BASIC and SHOP), and moving onto one of those for the first time picks it up, broadcast right after that space's `player_moved` event:

```json
{"type":"suit_picked_up","playerId":"...","spaceId":"...","suit":"HEART"}
```

Nothing is broadcast for a suit a player already holds -- picking one up again has no effect.

If movement reaches a space with more than one path out of it, it pauses there instead of a `player_moved` broadcast, and lists the options:

```json
{"type":"choice_required","playerId":"...","spaceId":"...","options":[{"toSpaceId":"...","branchOrder":0},{"toSpaceId":"...","branchOrder":1}]}
```

Reply from that same player's socket with the space to move onto, and movement picks back up (pausing again if it hits another branch):

```json
{"type":"choose_path","spaceId":"..."}
```

If movement instead runs out on a SHOP space nobody owns yet, it pauses there too and offers the purchase:

```json
{"type":"shop_purchase_available","playerId":"...","spaceId":"...","price":300}
```

Reply from that same player's socket to buy it or pass:

```json
{"type":"buy_shop"}
{"type":"decline_shop"}
```

Buying broadcasts the purchase, then a district recalculation if it brought the buyer's owned count in that district to 2 or more (existing shops get boosted by `existingShopBoostPercentage`, the one just bought by the larger `newShopBoostPercentage`):

```json
{"type":"shop_purchased","playerId":"...","spaceId":"...","price":300}
{"type":"district_values_recalculated","playerId":"...","districtId":"...","newValuesBySpaceId":{"...":330,"...":220}}
```

Either way, the turn ends right after.

Once movement is exhausted (or a shop decision is made), all sockets see the turn end, and — once the game hits its max turn count — an additional game-over event:

```json
{"type":"turn_ended","turnNumber":0,"playerId":"..."}
{"type":"game_over","turnCount":10}
```

Computer players (players added without a `userId`) never send any of this themselves — the server rolls and moves them automatically, randomly picking a path any time it hits a branch, and broadcasts the results the same way.

Session state is kept in memory per server instance, so this only works against a single running instance, not a load-balanced setup.
