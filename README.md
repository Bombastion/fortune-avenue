# Fortune Avenue

A web-based recreation of [Fortune Street](https://en.wikipedia.org/wiki/Fortune_Street), the board game video game that combines Mario Party-esque board gameplay with a stock market/property-trading layer. Fortune Avenue rebuilds it as something playable in the browser.

## Technical details for nerds

If you're curious how things are calculated, I found [this incredibly helpful blog post](https://bluepichu.wordpress.com/2012/08/07/fortune-street-calculations-part-1-starting-conditions/) where the author was trying to do basically this project back in 2012.

### Client

The client lives in [`client/`](client/) and is a React + TypeScript app built with Vite. It's an early skeleton right now — it calls the backend's `/health` endpoint and shows whether the API is reachable, with real gameplay UI to follow.

See the [client README](client/README.md) for how it fits together with the backend and reverse proxy.

### Server

The server lives in [`service/`](service/) and is a Kotlin + Spring Boot application. It exposes a WebSocket endpoint for real-time gameplay and a small REST API for everything else, backed by a Postgres database. The whole stack — backend, client, Postgres, and an nginx reverse proxy in front of the first two — runs in Docker via the [`docker-compose.yml`](docker-compose.yml) at the project root; run `make up` from here to build and start all of it. The [server README](service/README.md) and [client README](client/README.md) also each have their own `docker-compose.yml` and `Makefile` for running just the backend or just the client on their own — each sets its own explicit compose project name (`fortune-avenue`, `fortune-avenue-service`, `fortune-avenue-client`) so containers, networks, and volumes stay clearly namespaced instead of inheriting a bare directory name like `service`.

See the [server README](service/README.md) for details about how the backend works if you're interested.

## Running an example game (also for nerds (for now))

This walks through starting the server, setting up a game via REST, and playing it out over the WebSocket endpoint. All commands assume the server is running locally on `localhost:8080` (`make up` from `service/`, which starts just the backend + Postgres — see the [server README](service/README.md)).

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
      { "spaceType": "BASIC", "districtIndex": 0 },
      { "spaceType": "HEART" },
      { "spaceType": "DIAMOND" },
      { "spaceType": "SPADE" },
      { "spaceType": "CLUB" },
      { "spaceType": "BANK" }
    ],
    "paths": [
      { "from": 0, "to": 1, "branchOrder": 0 },
      { "from": 1, "to": 2, "branchOrder": 0 },
      { "from": 2, "to": 3, "branchOrder": 0 },
      { "from": 3, "to": 4, "branchOrder": 0 },
      { "from": 4, "to": 5, "branchOrder": 0 },
      { "from": 5, "to": 6, "branchOrder": 0 },
      { "from": 6, "to": 7, "branchOrder": 0 },
      { "from": 7, "to": 0, "branchOrder": 0 }
    ],
    "startSpaceIndex": 0,
    "startingGold": 1500,
    "baseSalary": 300,
    "promotionBonus": 100,
    "districts": [
      {
        "name": "Blue District",
        "colorHex": "1E90FF",
        "progressions": [
          { "ownedShopCount": 2, "priceMultiplier": 1.1000, "maxCapitalMultiplier": 1.1500 }
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

A district's `progressions` describe how a player's dominance there (how many of its shops they own at once) scales two things, looked up fresh by `ownedShopCount` every time rather than compounded: `priceMultiplier` scales the toll every shop they own in the district charges, and `maxCapitalMultiplier` scales how far each of those shops' value can be invested above its `baseValue`. Both must be greater than 1 (a dominant player should charge more and have more room to invest, never less). Any district with 2 or more spaces needs exactly one entry per `ownedShopCount` from 2 up to its total space count; a district with fewer spaces needs none.

A district's stock price (`currentStockValue`) is computed the same way the real game does it, per FortuneStreetModding's own board editor and district simulator tools: the average `currentValue` of the district's SHOP spaces, floored to an integer, then multiplied by a fixed 16.16 fixed-point constant (`0x0B00 / 0x10000`, roughly 4.3%) and floored again. This isn't configurable per board -- it's the same constant for every district in every game. When a game starts, the result is seeded onto a per-game `game_district_information` row for every district that actually contains at least one SHOP space.

A board's `startingGold` is how much gold every player in a game on that board starts with -- it's copied onto each player's state the moment they're added to a game (see `POST /games/{gameId}/players` below).

A board's `spaces` must include at least one BANK space and at least one of each suit (HEART, DIAMOND, SPADE, CLUB) -- a board missing any of these is rejected at creation, since a BANK space is what triggers a promotion payout, and that payout requires a player to be able to hold all 4 suits at once. `baseSalary` and `promotionBonus` are that payout's base amount and per-level bonus: a player who passes or lands on a BANK space while holding all 4 suits is paid `baseSalary + (promotionBonus * however many times they've already collected the promotion this game) + the current value of every shop they own`, then has their suits cleared and their promotion count bumped by one for next time. `baseSalary` must be a positive integer; `promotionBonus` must be zero or a positive integer.

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

The same space is also checked for a promotion -- a board can have a BANK space too, and passing or landing on one while currently holding all 4 suits clears them, bumps a promotion count, and pays out gold per the `baseSalary`/`promotionBonus` formula described above, broadcast right after that space's `player_moved` event:

```json
{"type":"promoted","playerId":"...","spaceId":"...","goldAwarded":600}
```

Nothing is broadcast for a BANK space visited without holding all 4 suits.

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

Buying broadcasts the purchase. It never changes any shop's value directly -- but if it brought the buyer's owned count in that district to 2 or more, every shop they own there (including the one just bought) gets its investable headroom recalculated using `maxCapitalMultiplier` for that new count, and every toll paid on any of those shops from then on is scaled by `priceMultiplier` for that count, computed fresh at toll time rather than stored:

```json
{"type":"shop_purchased","playerId":"...","spaceId":"...","price":300}
```

If movement instead runs out on a SHOP the player already owns themselves, and that shop still has investable headroom left (its `maxCapitalMultiplier`-derived ceiling not yet reached), it pauses there too and offers the investment:

```json
{"type":"investment_available","playerId":"...","spaceId":"...","currentValue":400,"maxCap":50}
```

Reply from that same player's socket to invest some of their own gold into it or pass:

```json
{"type":"invest","amount":30}
{"type":"decline_invest"}
```

`amount` must be between 1 and 999, and can't exceed either the shop's own remaining headroom (`maxCap` above) or the player's own gold; `invest` comes back as an `error` instead if it does, leaving the decision still pending. Investing broadcasts the result and ends the turn, exactly like buying a shop does:

```json
{"type":"invested","playerId":"...","spaceId":"...","amount":30,"newCurrentValue":430,"newMaxCap":20}
```

Landing on a shop you own with no headroom left doesn't pause at all -- exactly as landing on one you own has always worked. Either way -- bought, invested, or declined -- the turn ends right after.

Once movement is exhausted (or a shop decision is made), all sockets see the turn end, and — once the game hits its max turn count — an additional game-over event:

```json
{"type":"turn_ended","turnNumber":0,"playerId":"..."}
{"type":"game_over","turnCount":10}
```

Computer players (players added without a `userId`) never send any of this themselves — the server rolls and moves them automatically, randomly picking a path any time it hits a branch, and broadcasts the results the same way.

Session state is kept in memory per server instance, so this only works against a single running instance, not a load-balanced setup.
