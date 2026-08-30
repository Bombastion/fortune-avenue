import { describe, expect, it } from "vitest";
import { matchesUsernameQuery } from "./filter";

describe("matchesUsernameQuery", () => {
  it("matches case-insensitively anywhere in the username", () => {
    expect(matchesUsernameQuery("Alice", "ali")).toBe(true);
    expect(matchesUsernameQuery("Alice", "ALI")).toBe(true);
    expect(matchesUsernameQuery("Alice", "lic")).toBe(true);
  });

  it("rejects a query that isn't a substring", () => {
    expect(matchesUsernameQuery("Alice", "bob")).toBe(false);
  });

  it("treats a blank query as matching everything", () => {
    expect(matchesUsernameQuery("Alice", "")).toBe(true);
    expect(matchesUsernameQuery("Alice", "   ")).toBe(true);
  });
});
