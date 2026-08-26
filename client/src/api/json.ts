// See the comment at the top of api/types.ts: certain request fields must reach the server as
// unquoted JSON numeric literals with an exact decimal scale (e.g. 0.5000, not 0.5 and not
// "0.5000"), because the server deserializes them straight into java.math.BigDecimal and validates
// their scale. JSON.stringify has no way to express that -- it either emits a JS number (which
// silently drops trailing zeros) or a quoted string (which the server won't coerce to a number).
//
// The fix: keep these fields as validated 4-decimal-digit strings all the way through the form
// state, JSON.stringify the request as normal (so they come out as quoted strings), and then strip
// the quotes off just those known field names with a targeted regex pass. Every field name passed
// in here is a compile-time-known literal from a request DTO, and the value pattern it matches
// (`-?\d+\.\d{4}`) is exactly what the form validation already guarantees, so this can't
// accidentally unquote unrelated data.
const DECIMAL_FIELD_NAMES = [
  "basePricePercentage",
  "minimumStockPercentage",
  "existingShopBoostPercentage",
  "newShopBoostPercentage",
] as const;

export function serializeRequestWithDecimals(request: unknown): string {
  let json = JSON.stringify(request);

  for (const field of DECIMAL_FIELD_NAMES) {
    const quoted = new RegExp(`"${field}":"(-?\\d+\\.\\d{4})"`, "g");
    json = json.replace(quoted, `"${field}":$1`);
  }

  return json;
}
