/**
 * Case-insensitive substring match against a username -- used to filter a client-side list of
 * users as someone types into a search box. See UsersPage's searchable table and
 * GameDetailPage's player picker.
 */
export function matchesUsernameQuery(username: string, query: string): boolean {
  const trimmed = query.trim().toLowerCase();
  if (trimmed.length === 0) return true;
  return username.toLowerCase().includes(trimmed);
}
