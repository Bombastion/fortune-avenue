import { describe, expect, it } from "vitest";
import {
  isBlank,
  isFourDigitDecimalString,
  isFourDigitFractionStrictlyBetweenZeroAndOne,
  isHexColor,
  isNonNegativeIntegerString,
  isPositiveFourDigitDecimalString,
  isPositiveIntegerString,
} from "./rules";

describe("isFourDigitDecimalString", () => {
  it("accepts exactly 4 digits after the decimal point", () => {
    expect(isFourDigitDecimalString("0.1234")).toBe(true);
    expect(isFourDigitDecimalString("1.0000")).toBe(true);
    expect(isFourDigitDecimalString("-1.2345")).toBe(true);
  });

  it("rejects the wrong number of decimal digits", () => {
    expect(isFourDigitDecimalString("0.123")).toBe(false);
    expect(isFourDigitDecimalString("0.12345")).toBe(false);
    expect(isFourDigitDecimalString("0")).toBe(false);
  });

  it("rejects non-numeric input", () => {
    expect(isFourDigitDecimalString("abc")).toBe(false);
    expect(isFourDigitDecimalString("")).toBe(false);
  });

  it("trims surrounding whitespace before checking", () => {
    expect(isFourDigitDecimalString("  0.1234  ")).toBe(true);
  });
});

describe("isFourDigitFractionStrictlyBetweenZeroAndOne", () => {
  it("accepts values strictly between 0 and 1 with exactly 4 digits", () => {
    expect(isFourDigitFractionStrictlyBetweenZeroAndOne("0.5000")).toBe(true);
    expect(isFourDigitFractionStrictlyBetweenZeroAndOne("0.9999")).toBe(true);
    expect(isFourDigitFractionStrictlyBetweenZeroAndOne("0.0001")).toBe(true);
  });

  it("rejects the boundaries and anything outside them", () => {
    expect(isFourDigitFractionStrictlyBetweenZeroAndOne("0.0000")).toBe(false);
    expect(isFourDigitFractionStrictlyBetweenZeroAndOne("1.0000")).toBe(false);
    expect(isFourDigitFractionStrictlyBetweenZeroAndOne("-0.1234")).toBe(false);
    expect(isFourDigitFractionStrictlyBetweenZeroAndOne("1.5000")).toBe(false);
  });

  it("still rejects a value with the wrong scale even if in range", () => {
    expect(isFourDigitFractionStrictlyBetweenZeroAndOne("0.5")).toBe(false);
  });
});

describe("isPositiveFourDigitDecimalString", () => {
  it("accepts any positive value with exactly 4 digits (no upper bound)", () => {
    expect(isPositiveFourDigitDecimalString("0.1000")).toBe(true);
    expect(isPositiveFourDigitDecimalString("5.0000")).toBe(true);
  });

  it("rejects zero and negative values", () => {
    expect(isPositiveFourDigitDecimalString("0.0000")).toBe(false);
    expect(isPositiveFourDigitDecimalString("-0.1000")).toBe(false);
  });
});

describe("isHexColor", () => {
  it("accepts exactly 6 hex characters, either case", () => {
    expect(isHexColor("1E90FF")).toBe(true);
    expect(isHexColor("1e90ff")).toBe(true);
    expect(isHexColor("000000")).toBe(true);
  });

  it("rejects the wrong length or invalid characters", () => {
    expect(isHexColor("1E90F")).toBe(false);
    expect(isHexColor("1E90FFF")).toBe(false);
    expect(isHexColor("1E90FG")).toBe(false);
  });
});

describe("isPositiveIntegerString", () => {
  it("accepts positive whole numbers only", () => {
    expect(isPositiveIntegerString("1")).toBe(true);
    expect(isPositiveIntegerString("1500")).toBe(true);
  });

  it("rejects zero, negatives, decimals, and empty input", () => {
    expect(isPositiveIntegerString("0")).toBe(false);
    expect(isPositiveIntegerString("-1")).toBe(false);
    expect(isPositiveIntegerString("1.5")).toBe(false);
    expect(isPositiveIntegerString("")).toBe(false);
    expect(isPositiveIntegerString("abc")).toBe(false);
  });
});

describe("isNonNegativeIntegerString", () => {
  it("accepts zero and positive whole numbers", () => {
    expect(isNonNegativeIntegerString("0")).toBe(true);
    expect(isNonNegativeIntegerString("42")).toBe(true);
  });

  it("rejects negatives, decimals, and empty input", () => {
    expect(isNonNegativeIntegerString("-1")).toBe(false);
    expect(isNonNegativeIntegerString("1.5")).toBe(false);
    expect(isNonNegativeIntegerString("")).toBe(false);
  });
});

describe("isBlank", () => {
  it("treats empty and whitespace-only strings as blank", () => {
    expect(isBlank("")).toBe(true);
    expect(isBlank("   ")).toBe(true);
  });

  it("treats anything with non-whitespace content as not blank", () => {
    expect(isBlank("a")).toBe(false);
    expect(isBlank("  a  ")).toBe(false);
  });
});
