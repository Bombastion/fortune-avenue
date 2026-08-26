import { describe, expect, it } from "vitest";
import { serializeRequestWithDecimals } from "./json";

describe("serializeRequestWithDecimals", () => {
  it("unquotes a known decimal field and preserves its trailing zeros", () => {
    const json = serializeRequestWithDecimals({ basePricePercentage: "0.1200", name: "Test" });

    // The literal text must still say "0.1200" -- if this had gone through JSON.stringify on a
    // plain JS number instead, the trailing zero would have silently disappeared (0.12).
    expect(json).toContain('"basePricePercentage":0.1200');
    expect(json).not.toContain('"basePricePercentage":"0.1200"');

    const parsed = JSON.parse(json);
    expect(parsed).toEqual({ basePricePercentage: 0.12, name: "Test" });
  });

  it("unquotes every known decimal field, however deeply nested", () => {
    const request = {
      name: "Board",
      districts: [
        {
          minimumStockPercentage: "0.5000",
          progressions: [
            { ownedShopCount: 2, existingShopBoostPercentage: "0.1000", newShopBoostPercentage: "0.2000" },
          ],
        },
      ],
    };

    const json = serializeRequestWithDecimals(request);

    expect(json).toContain('"minimumStockPercentage":0.5000');
    expect(json).toContain('"existingShopBoostPercentage":0.1000');
    expect(json).toContain('"newShopBoostPercentage":0.2000');

    const parsed = JSON.parse(json);
    expect(parsed.districts[0].minimumStockPercentage).toBe(0.5);
    expect(parsed.districts[0].progressions[0].existingShopBoostPercentage).toBe(0.1);
  });

  it("leaves unrelated fields quoted, even ones with a similar-looking value", () => {
    const json = serializeRequestWithDecimals({ otherPercentage: "0.1234", label: "hello" });

    expect(json).toContain('"otherPercentage":"0.1234"');
    expect(json).toContain('"label":"hello"');
  });

  it("only unquotes values that actually match the exact-4-digit-decimal pattern", () => {
    // Defensive: even for a known field name, a value that isn't a valid 4-digit decimal (which
    // form validation should have already rejected before this is ever called) is left alone
    // rather than silently emitted as an invalid unquoted literal.
    const json = serializeRequestWithDecimals({ basePricePercentage: "0.5" });

    expect(json).toBe('{"basePricePercentage":"0.5"}');
  });
});
