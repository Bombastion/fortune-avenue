// Client-side mirrors of the server's validation rules, so a mistake gets caught while someone is
// still filling out the form instead of round-tripping to the server first. See:
//   - service/.../service/ShopSpaceValidator.kt
//   - service/.../service/DistrictValidator.kt
//   - service/.../service/DistrictProgressionValidator.kt
//   - service/.../service/RequiredSpaceTypesValidator.kt
//   - service/.../service/BoardGraphValidator.kt
// The server remains the source of truth -- these are duplicated on purpose for instant feedback,
// not to replace server-side validation.

const FOUR_DIGIT_DECIMAL_PATTERN = /^-?\d+\.\d{4}$/;
const HEX_COLOR_PATTERN = /^[0-9A-Fa-f]{6}$/;

/** True if [raw] is a decimal string with exactly 4 digits after the decimal point. */
export function isFourDigitDecimalString(raw: string): boolean {
  return FOUR_DIGIT_DECIMAL_PATTERN.test(raw.trim());
}

/** True if [raw] has exactly 4 decimal digits and represents a value strictly between 0 and 1. */
export function isFourDigitFractionStrictlyBetweenZeroAndOne(raw: string): boolean {
  if (!isFourDigitDecimalString(raw)) return false;
  const value = Number(raw);
  return value > 0 && value < 1;
}

/** True if [raw] has exactly 4 decimal digits and represents a positive value. */
export function isPositiveFourDigitDecimalString(raw: string): boolean {
  if (!isFourDigitDecimalString(raw)) return false;
  return Number(raw) > 0;
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
