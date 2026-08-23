#!/usr/bin/env python3
"""
Fortune Avenue -- simulate a 2-human-player game end to end.
 
Creates a board, a game, two users, and two players via the REST API, then opens
one WebSocket connection per player and plays the whole game out automatically
(rolling dice, resolving branch choices, buying shops, and trading stock) until
the server sends `game_over`.
 
Both players are real ("human") players -- i.e. created with a `userId`, exactly
like a person would be -- rather than the server's built-in computer opponents.
This script just stands in for the humans, making reasonably sensible automated
choices so you can watch (or replay) a full game without two people needing to
sit at two keyboards.
 
Usage:
    pip3 install websockets
    python3 simulate_2p_game.py [--host localhost] [--port 8080]
                                 [--player1 "Player One"] [--player2 "Player Two"]
                                 [--seed 42] [--log game_transcript.json]
 
Requires the Fortune Avenue server to already be running and reachable at
http://<host>:<port> (see service/README.md -- `make up` from service/).
"""
 
from __future__ import annotations
 
import argparse
import asyncio
import json
import random
import sys
import urllib.error
import urllib.request
 
try:
    import websockets
except ImportError:
    print(
        "Missing dependency 'websockets'. Install it first:\n\n"
        "    pip3 install websockets\n",
        file=sys.stderr,
    )
    sys.exit(1)
 
 
# ---------------------------------------------------------------------------
# Board definition
#
# A small closed-loop board (12 spaces) with a fork/merge in the middle (to
# exercise choice_required), two districts of two shops each (to exercise the
# district value recalculation once a player owns both shops in a district),
# and one of every suit plus a BANK space (required by the server, and needed
# to exercise the promotion payout). Decimal fields are written out by hand
# below (not built with json.dumps) so the trailing zeros survive -- the
# server requires basePricePercentage / minimumStockPercentage / the boost
# percentages to have *exactly* 4 digits after the decimal point, and
# json.dumps(0.2500) would collapse that to "0.25".
# ---------------------------------------------------------------------------
BOARD_BODY = """{
  "name": "Simulation Board",
  "spaces": [
    { "spaceType": "BASIC" },
    { "spaceType": "SHOP", "baseValue": 300, "basePricePercentage": 0.2500, "districtIndex": 0 },
    { "spaceType": "SHOP", "baseValue": 280, "basePricePercentage": 0.2500, "districtIndex": 0 },
    { "spaceType": "HEART" },
    { "spaceType": "BASIC" },
    { "spaceType": "DIAMOND" },
    { "spaceType": "SPADE" },
    { "spaceType": "BASIC" },
    { "spaceType": "SHOP", "baseValue": 320, "basePricePercentage": 0.2500, "districtIndex": 1 },
    { "spaceType": "SHOP", "baseValue": 260, "basePricePercentage": 0.2500, "districtIndex": 1 },
    { "spaceType": "CLUB" },
    { "spaceType": "BANK" }
  ],
  "paths": [
    { "from": 0, "to": 1, "branchOrder": 0 },
    { "from": 1, "to": 2, "branchOrder": 0 },
    { "from": 2, "to": 3, "branchOrder": 0 },
    { "from": 3, "to": 4, "branchOrder": 0 },
    { "from": 4, "to": 5, "branchOrder": 0 },
    { "from": 4, "to": 7, "branchOrder": 1 },
    { "from": 5, "to": 6, "branchOrder": 0 },
    { "from": 6, "to": 8, "branchOrder": 0 },
    { "from": 7, "to": 8, "branchOrder": 0 },
    { "from": 8, "to": 9, "branchOrder": 0 },
    { "from": 9, "to": 10, "branchOrder": 0 },
    { "from": 10, "to": 11, "branchOrder": 0 },
    { "from": 11, "to": 0, "branchOrder": 0 }
  ],
  "startSpaceIndex": 0,
  "startingGold": 1500,
  "baseSalary": 300,
  "promotionBonus": 100,
  "districts": [
    {
      "name": "Blue District",
      "colorHex": "1E90FF",
      "minimumStockPercentage": 0.5000,
      "progressions": [
        { "ownedShopCount": 2, "existingShopBoostPercentage": 0.1000, "newShopBoostPercentage": 0.1500 }
      ]
    },
    {
      "name": "Red District",
      "colorHex": "DC143C",
      "minimumStockPercentage": 0.5000,
      "progressions": [
        { "ownedShopCount": 2, "existingShopBoostPercentage": 0.1000, "newShopBoostPercentage": 0.1500 }
      ]
    }
  ]
}"""
 
STARTING_GOLD = 1500  # must match BOARD_BODY's "startingGold" above
STOCK_BUY_QUANTITY = 5
 
 
# ---------------------------------------------------------------------------
# REST helpers
# ---------------------------------------------------------------------------
def rest_post(base_url: str, path: str, body_dict: dict | None = None, raw_body: str | None = None):
    url = f"{base_url}{path}"
    data = (raw_body if raw_body is not None else json.dumps(body_dict or {})).encode("utf-8")
    req = urllib.request.Request(
        url, data=data, method="POST", headers={"Content-Type": "application/json"}
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        print(f"REST call failed: POST {path} -> HTTP {e.code}\n{body}", file=sys.stderr)
        raise SystemExit(1)
    except urllib.error.URLError as e:
        print(
            f"Could not reach {url} ({e.reason}).\n"
            "Is the server running? (see service/README.md -- `make up` from service/)",
            file=sys.stderr,
        )
        raise SystemExit(1)
 
 
def set_up_game(base_url: str, player1_name: str, player2_name: str) -> dict:
    print(f"Creating board on {base_url} ...")
    board = rest_post(base_url, "/boards", raw_body=BOARD_BODY)
    board_id = board["id"]
    print(f"  board id: {board_id}")
 
    print("Creating game ...")
    game = rest_post(base_url, "/games", {"boardId": board_id})
    game_id = game["id"]
    print(f"  game id: {game_id}  (targetNetWorth={game['targetNetWorth']})")
 
    players = []
    for name in (player1_name, player2_name):
        print(f"Creating user '{name}' ...")
        user = rest_post(base_url, "/users", {"username": name})
        print(f"  user id: {user['id']}")
 
        print(f"Adding '{name}' as a player ...")
        player = rest_post(base_url, f"/games/{game_id}/players", {"userId": user["id"]})
        print(f"  player id: {player['id']}")
        players.append({"name": name, "userId": user["id"], "playerId": player["id"]})
 
    return {"boardId": board_id, "gameId": game_id, "players": players}
 
 
# ---------------------------------------------------------------------------
# WebSocket-driven gameplay
# ---------------------------------------------------------------------------
class GameSimulation:
    def __init__(self, host: str, port: int, game_id: str, players: list[dict], seed: int | None):
        self.ws_url = f"ws://{host}:{port}/ws/game"
        self.game_id = game_id
        self.players = players  # [{name, userId, playerId}, ...]
        self.name_by_id = {p["playerId"]: p["name"] for p in players}
        self.rng = random.Random(seed)
 
        self.gold = {p["playerId"]: STARTING_GOLD for p in players}
        self.shops_owned: dict[str, list[str]] = {p["playerId"]: [] for p in players}
        self.suits_held: dict[str, set] = {p["playerId"]: set() for p in players}
        self.promotions = {p["playerId"]: 0 for p in players}
        self.stocks: dict[str, dict] = {p["playerId"]: {} for p in players}
        self.transcript: list[dict] = []
        self.game_over_event: dict | None = None
 
    def label(self, player_id: str) -> str:
        return self.name_by_id.get(player_id, player_id)
 
    def log(self, event: dict) -> None:
        self.transcript.append(event)
        t = event.get("type")
        pid = event.get("playerId")
        who = self.label(pid) if pid else None
 
        if t == "player_ready":
            print(f"  [ready] {who} is ready")
        elif t == "game_started":
            order = ", ".join(self.label(p) for p in event["turnOrder"])
            print(f"[game_started] turn order: {order}")
        elif t == "turn_started":
            print(f"\n--- turn {event['turnNumber']}: {who}'s turn ---")
        elif t == "dice_rolled":
            print(f"  {who} rolled a {event['roll']}")
        elif t == "player_moved":
            print(f"    -> moved to space {event['toSpaceId'][:8]} ({event['movementPointsRemaining']} to go)")
        elif t == "suit_picked_up":
            print(f"    {who} picked up {event['suit']}")
        elif t == "promoted":
            print(f"    *** {who} promoted! +{event['goldAwarded']} gold ***")
        elif t == "choice_required":
            print(f"    {who} must choose a path at a fork")
        elif t == "shop_purchase_available":
            print(f"    shop available for {who} at price {event['price']}")
        elif t == "shop_purchased":
            print(f"    {who} bought the shop for {event['price']}")
        elif t == "district_values_recalculated":
            print(f"    district {event['districtId'][:8]} values recalculated: {event['newValuesBySpaceId']}")
        elif t == "stock_trading_available":
            print(f"    stock trading available for {who}: {event['offers']}")
        elif t == "stock_purchased":
            print(f"    {who} bought {event['quantity']} shares of district {event['districtId'][:8]} for {event['totalCost']}")
        elif t == "stock_sold":
            print(f"    {who} sold {event['quantity']} shares of district {event['districtId'][:8]} for {event['totalProceeds']}")
        elif t == "turn_ended":
            print(f"  --- {who}'s turn ended ---")
        elif t == "game_over":
            print(f"\n[game_over] after {event['turnCount']} turns")
        elif t == "error":
            print(f"    !! server error: {event['message']}")
        else:
            print(f"    {event}")
 
    def apply_state(self, event: dict) -> None:
        t = event.get("type")
        pid = event.get("playerId")
        if t == "shop_purchased":
            self.gold[pid] -= event["price"]
            self.shops_owned[pid].append(event["spaceId"])
        elif t == "suit_picked_up":
            self.suits_held[pid].add(event["suit"])
        elif t == "promoted":
            self.gold[pid] += event["goldAwarded"]
            self.promotions[pid] += 1
            self.suits_held[pid].clear()
        elif t == "stock_purchased":
            self.gold[pid] -= event["totalCost"]
            self.stocks[pid][event["districtId"]] = (
                self.stocks[pid].get(event["districtId"], 0) + event["quantity"]
            )
        elif t == "stock_sold":
            self.gold[pid] += event["totalProceeds"]
            self.stocks[pid][event["districtId"]] = (
                self.stocks[pid].get(event["districtId"], 0) - event["quantity"]
            )
 
    async def send(self, sockets: dict, player_id: str, message: dict) -> None:
        await sockets[player_id].send(json.dumps(message))
 
    async def act_on(self, sockets: dict, event: dict) -> None:
        """Decide and send the client message a pending event requires, if any."""
        t = event["type"]
        pid = event.get("playerId")
 
        if t == "turn_started":
            await self.send(sockets, pid, {"type": "roll_dice"})
 
        elif t == "choice_required":
            options = event["options"]
            choice = self.rng.choice(options)
            await self.send(sockets, pid, {"type": "choose_path", "spaceId": choice["toSpaceId"]})
 
        elif t == "shop_purchase_available":
            if self.gold[pid] >= event["price"]:
                await self.send(sockets, pid, {"type": "buy_shop"})
            else:
                await self.send(sockets, pid, {"type": "decline_shop"})
 
        elif t == "stock_trading_available":
            affordable = [
                o for o in event["offers"] if o["pricePerShare"] * STOCK_BUY_QUANTITY <= self.gold[pid]
            ]
            if affordable:
                offer = self.rng.choice(affordable)
                await self.send(
                    sockets,
                    pid,
                    {
                        "type": "buy_stock",
                        "districtId": offer["districtId"],
                        "quantity": STOCK_BUY_QUANTITY,
                    },
                )
            else:
                await self.send(sockets, pid, {"type": "skip_stock_trade"})
 
    async def play(self) -> None:
        urls = {
            p["playerId"]: f"{self.ws_url}?gameId={self.game_id}&playerId={p['playerId']}"
            for p in self.players
        }
        p1, p2 = self.players[0]["playerId"], self.players[1]["playerId"]
 
        async with websockets.connect(urls[p1]) as ws1, websockets.connect(urls[p2]) as ws2:
            sockets = {p1: ws1, p2: ws2}
 
            # Each socket gets its own `connected` event first.
            for pid, ws in sockets.items():
                connected = json.loads(await ws.recv())
                assert connected["type"] == "connected" and connected["playerId"] == pid, connected
 
            # Watch player 2's socket in the background purely for diagnostics --
            # per-connection `error` events land only on the socket that sent the
            # offending message, and everything else is broadcast identically to
            # both sockets, so all game-state handling below reads from ws1 only.
            async def watch_secondary():
                try:
                    async for raw in ws2:
                        event = json.loads(raw)
                        if event.get("type") == "error":
                            print(f"    !! server error (on {self.label(p2)}'s socket): {event['message']}")
                except websockets.ConnectionClosed:
                    pass
 
            watcher = asyncio.create_task(watch_secondary())
 
            print("Both players connected. Marking ready ...")
            await sockets[p1].send(json.dumps({"type": "ready"}))
            await sockets[p2].send(json.dumps({"type": "ready"}))
 
            async for raw in ws1:
                event = json.loads(raw)
                self.log(event)
                self.apply_state(event)
 
                if event["type"] == "game_over":
                    self.game_over_event = event
                    break
 
                if event.get("playerId") in sockets:
                    await self.act_on(sockets, event)
 
            watcher.cancel()
 
    def print_summary(self) -> None:
        print("\n" + "=" * 60)
        print("SUMMARY (gold/holdings tracked client-side from events --")
        print("there's no REST endpoint that exposes live player state)")
        print("=" * 60)
        for p in self.players:
            pid = p["playerId"]
            print(f"\n{p['name']} ({pid}):")
            print(f"  estimated gold remaining: {self.gold[pid]}")
            print(f"  shops owned: {len(self.shops_owned[pid])} {self.shops_owned[pid]}")
            print(f"  suits currently held: {sorted(self.suits_held[pid])}")
            print(f"  promotions: {self.promotions[pid]}")
            print(f"  stock holdings: {self.stocks[pid]}")
        if self.game_over_event:
            print(f"\nGame ended after {self.game_over_event['turnCount']} turns.")
 
 
async def main_async(args: argparse.Namespace) -> None:
    base_url = f"http://{args.host}:{args.port}"
 
    setup = set_up_game(base_url, args.player1, args.player2)
 
    print("\nOpening WebSocket connections and playing the game out ...\n")
    sim = GameSimulation(args.host, args.port, setup["gameId"], setup["players"], args.seed)
    await sim.play()
    sim.print_summary()
 
    if args.log:
        with open(args.log, "w") as f:
            json.dump(
                {"setup": setup, "transcript": sim.transcript},
                f,
                indent=2,
            )
        print(f"\nFull transcript written to {args.log}")
 
 
def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--player1", default="Player One")
    parser.add_argument("--player2", default="Player Two")
    parser.add_argument("--seed", type=int, default=None, help="seed the RNG for reproducible choices")
    parser.add_argument("--log", default="game_transcript.json", help="path to write the full JSON event transcript (empty string to skip)")
    args = parser.parse_args()
    if args.log == "":
        args.log = None
 
    try:
        asyncio.run(main_async(args))
    except KeyboardInterrupt:
        print("\nInterrupted.")
 
 
if __name__ == "__main__":
    main()