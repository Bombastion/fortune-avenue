import { describe, expect, it } from "vitest";
import {
  isBlank,
  isFractionStrictlyBetweenZeroAndOne,
  isHexColor,
  isNonNegativeIntegerString,
  isPositiveDecimalString,
  isPositiveIntegerString,
  toFixedDecimalString,
} from "./rules";

describe("isFractionStrictlyBetweenZeroAndOne", () => {
  it("accepts any equivalent way of writing a value strictly between 0 and 1", () => {
    expect(isFractionStrictlyBetweenZeroAndOne(".05")).toBe(true);
    expect(isFractionStrictlyBetweenZeroAndOne("0.05")).toBe(true);
    expect(isFractionStrictlyBetweenZeroAndOne("0.0500")).toBe(true);
    expect(isFractionStrictlyBetweenZeroAndOne("0.9999")).toBe(true);
  });

  it("rejects the boundaries and anything outside them", () => {
    expect(isFractionStrictlyBetweenZeroAndOne("0")).toBe(false);
    expect(isFractionStrictlyBetweenZeroAndOne("0.0000")).toBe(false);
    expect(isFractionStrictlyBetweenZeroAndOne("1")).toBe(false);
    expect(isFractionStrictlyBetweenZeroAndOne("1.0000")).toBe(false);
    expect(isFractionStrictlyBetweenZeroAndOne("-.5")).toBe(false);
    expect(isFractionStrictlyBetweenZeroAndOne("1.5")).toBe(false);
  });

  it("rejects more than 4 decimal digits rather than rounding", () => {
    expect(isFractionStrictlyBetweenZeroAndOne("0.12345")).toBe(false);
  });

  it("rejects non-numeric input", () => {
    expect(isFractionStrictlyBetweenZeroAndOne("abc")).toBe(false);
    expect(isFractionStrictlyBetweenZeroAndOne("")).toBe(false);
    expect(isFractionStrictlyBetweenZeroAndOne("1e-2")).toBe(false);
  });

  it("trims surrounding whitespace before checking", () => {
    expect(isFractionStrictlyBetweenZeroAndOne("  0.05  ")).toBe(true);
  });
});

describe("isPositiveDecimalString", () => {
  it("accepts any positive value with at most 4 decimal digits (no upper bound)", () => {
    expect(isPositiveDecimalString(".1")).toBe(true);
    expect(isPositiveDecimalString("0.1")).toBe(true);
    expect(isPositiveDecimalString("0.1000")).toBe(true);
    expect(isPositiveDecimalString("5")).toBe(true);
    expect(isPositiveDecimalString("1000000.1234")).toBe(true);
  });

  it("rejects zero, negative values, and more than 4 decimal digits", () => {
    expect(isPositiveDecimalString("0")).toBe(false);
    expect(isPositiveDecimalString("0.0000")).toBe(false);
    expect(isPositiveDecimalString("-0.1")).toBe(false);
    expect(isPositiveDecimalString("0.10001")).toBe(false);
  });
});

describe("toFixedDecimalString", () => {
  it("pads a shorter decimal out to the exact 4-digit-scale string the server requires", () => {
    expect(toFixedDecimalString(".05")).toBe("0.0500");
    expect(toFixedDecimalString("0.05")).toBe("0.0500");
    expect(toFixedDecimalString("5")).toBe("5.0000");
    expect(toFixedDecimalString(".5")).toBe("0.5000");
  });

  it("leaves an already-4-digit value unchanged", () => {
    expect(toFixedDecimalString("0.1234")).toBe("0.1234");
  });

  it("supports a different target scale", () => {
    expect(toFixedDecimalString("5", 2)).toBe("5.00");
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
