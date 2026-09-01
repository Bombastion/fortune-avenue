/**
 * A fixed palette assigned to players by their position in turn order (or, before the game
 * starts, the order the players list came back in) -- cycles if there are more players than
 * colors, which no real board game needs to worry about but a dynamically-sized one might.
 */
const PLAYER_COLORS = ["#2e8bc7", "#d1425f", "#3f9142", "#c9a227", "#8a5fbf", "#c77d2e", "#3f4750", "#1f9e9e"];

export function playerColor(index: number): string {
  return PLAYER_COLORS[index % PLAYER_COLORS.length];
}
