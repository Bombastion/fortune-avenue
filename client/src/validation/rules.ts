// Client-side mirrors of the server's validation rules, so a mistake gets caught while someone is
// still filling out the form instead of round-tripping to the server first. See:
//   - service/.../service/ShopSpaceValidator.kt
//   - service/.../service/DistrictValidator.kt
//   - service/.../service/DistrictProgressionValidator.kt
//   - service/.../service/RequiredSpaceTypesValidator.kt
//   - service/.../service/BoardGraphValidator.kt
// The server remains the source of truth -- these are duplicated on purpose for instant feedback,
// not to replace server-side validation.

const HEX_COLOR_PATTERN = /^[0-9A-Fa-f]{6}$/;

/**
 * The server's BigDecimal percentage fields (basePricePercentage, minimumStockPercentage, the
 * boost percentages) must reach it with exactly this many digits after the decimal point -- see
 * api/json.ts. Someone filling out the form shouldn't have to type that themselves though: ".05"
 * and "0.0500" mean the same thing, and the form should accept either. So the rule here is the
 * opposite of what the server enforces -- at most this many decimal digits, not exactly -- and
 * toFixedDecimalString below pads a shorter value out to the server's exact format once validation
 * has passed. More than this many digits is rejected rather than rounded, since rounding would
 * silently throw away precision the person actually typed.
 */
export const DECIMAL_SCALE = 4;

interface ParsedDecimal {
  value: number;
  decimalDigits: number;
}

// Accepts any ordinary way of writing a decimal number -- "5", "5.", ".5", "0.5" -- but not
// scientific notation, thousands separators, or multiple decimal points.
const DECIMAL_NUMBER_PATTERN = /^-?(?:\d+\.?\d*|\.\d+)$/;

function parseDecimal(raw: string): ParsedDecimal | null {
  const trimmed = raw.trim();
  if (!DECIMAL_NUMBER_PATTERN.test(trimmed)) return null;

  const value = Number(trimmed);
  if (!Number.isFinite(value)) return null;

  const dotIndex = trimmed.indexOf(".");
  const decimalDigits = dotIndex === -1 ? 0 : trimmed.length - dotIndex - 1;

  return { value, decimalDigits };
}

/**
 * True if [raw] is a decimal value strictly between 0 and 1, with at most [DECIMAL_SCALE] digits
 * after the decimal point. Accepts any equivalent way of writing that value -- "0.05", ".05", and
 * "0.0500" are all fine.
 */
export function isFractionStrictlyBetweenZeroAndOne(raw: string): boolean {
  const parsed = parseDecimal(raw);
  if (!parsed || parsed.decimalDigits > DECIMAL_SCALE) return false;
  return parsed.value > 0 && parsed.value < 1;
}

/**
 * True if [raw] is a positive decimal value (no upper bound), with at most [DECIMAL_SCALE] digits
 * after the decimal point.
 */
export function isPositiveDecimalString(raw: string): boolean {
  const parsed = parseDecimal(raw);
  if (!parsed || parsed.decimalDigits > DECIMAL_SCALE) return false;
  return parsed.value > 0;
}

/**
 * Converts a decimal string already known to be valid (per isFractionStrictlyBetweenZeroAndOne or
 * isPositiveDecimalString above -- i.e. at most [DECIMAL_SCALE] decimal digits) into the exact
 * fixed-scale string the server requires, e.g. ".05" -> "0.0500". No rounding occurs: the input is
 * already within that precision, so this only ever pads, never truncates.
 */
export function toFixedDecimalString(raw: string, digits: number = DECIMAL_SCALE): string {
  return Number(raw.trim()).toFixed(digits);
}

export function isHexColor(raw: string): boolean {
  return HEX_COLOR_PATTERN.test(raw.trim());
}

/** True if [raw] parses as a positive (> 0) integer, with no stray characters. */
export function isPositiveIntegerString(raw: string): boolean {
  if (!/^\d+$/.test(raw.trim())) return false;
  return Number(raw) > 0;
}

/** True if [raw] parses as zero or a positive integer, with no stray characters. */
export function isNonNegativeIntegerString(raw: string): boolean {
  if (!/^\d+$/.test(raw.trim())) return false;
  return Number(raw) >= 0;
}

export function isBlank(raw: string): boolean {
  return raw.trim().length === 0;
}
