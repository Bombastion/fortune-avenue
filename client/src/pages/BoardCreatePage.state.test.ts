import { describe, expect, it } from "vitest";
import {
  type BoardFormState,
  type DistrictFormState,
  type PathFormState,
  type SpaceFormState,
  buildCreateBoardRequest,
  buildGraphPreview,
  emptyBoardForm,
  removeDistrictAt,
  removeSpaceAt,
  requiredProgressionLevels,
  spaceCountForDistrict,
  validateBoardForm,
} from "./BoardCreatePage.state";

describe("requiredProgressionLevels", () => {
  it("requires nothing below 2 spaces", () => {
    expect(requiredProgressionLevels(0)).toEqual([]);
    expect(requiredProgressionLevels(1)).toEqual([]);
  });

  it("requires one level per ownedShopCount from 2 up to the space count", () => {
    expect(requiredProgressionLevels(2)).toEqual([2]);
    expect(requiredProgressionLevels(3)).toEqual([2, 3]);
    expect(requiredProgressionLevels(5)).toEqual([2, 3, 4, 5]);
  });
});

describe("spaceCountForDistrict", () => {
  const spaces: SpaceFormState[] = [
    { localId: "s0", spaceType: "SHOP", baseValue: "", basePricePercentage: "", districtIndex: 0 },
    { localId: "s1", spaceType: "SHOP", baseValue: "", basePricePercentage: "", districtIndex: 1 },
    { localId: "s2", spaceType: "BASIC", baseValue: "", basePricePercentage: "", districtIndex: null },
    { localId: "s3", spaceType: "SHOP", baseValue: "", basePricePercentage: "", districtIndex: 0 },
  ];

  it("counts only spaces pointing at the given district index", () => {
    expect(spaceCountForDistrict(spaces, 0)).toBe(2);
    expect(spaceCountForDistrict(spaces, 1)).toBe(1);
    expect(spaceCountForDistrict(spaces, 2)).toBe(0);
  });
});

describe("removeSpaceAt", () => {
  function threeSpaceForm(): BoardFormState {
    const spaces: SpaceFormState[] = [
      { localId: "s0", spaceType: "BASIC", baseValue: "", basePricePercentage: "", districtIndex: null },
      { localId: "s1", spaceType: "BASIC", baseValue: "", basePricePercentage: "", districtIndex: null },
      { localId: "s2", spaceType: "BASIC", baseValue: "", basePricePercentage: "", districtIndex: null },
    ];
    const paths: PathFormState[] = [
      { localId: "p0", from: 0, to: 1, branchOrder: "0" }, // references the removed space (to)
      { localId: "p1", from: 1, to: 2, branchOrder: "0" }, // references the removed space (from)
      { localId: "p2", from: 0, to: 2, branchOrder: "0" }, // doesn't touch it, but "to" shifts down
    ];
    return { ...emptyBoardForm(), spaces, paths, startSpaceIndex: 1 };
  }

  it("drops any path touching the removed space, and shifts indices past it down by one", () => {
    const result = removeSpaceAt(threeSpaceForm(), 1);

    expect(result.spaces).toHaveLength(2);
    expect(result.paths).toEqual([{ localId: "p2", from: 0, to: 1, branchOrder: "0" }]);
  });

  it("clears the start space if it was the one removed", () => {
    const result = removeSpaceAt(threeSpaceForm(), 1);
    expect(result.startSpaceIndex).toBeNull();
  });

  it("decrements the start space if it came after the removed one, and leaves it alone if before", () => {
    const after = removeSpaceAt({ ...threeSpaceForm(), startSpaceIndex: 2 }, 1);
    expect(after.startSpaceIndex).toBe(1);

    const before = removeSpaceAt({ ...threeSpaceForm(), startSpaceIndex: 0 }, 1);
    expect(before.startSpaceIndex).toBe(0);
  });
});

describe("removeDistrictAt", () => {
  it("clears districtIndex on spaces pointing at the removed district, and shifts later indices down", () => {
    const spaces: SpaceFormState[] = [
      { localId: "s0", spaceType: "SHOP", baseValue: "", basePricePercentage: "", districtIndex: 0 },
      { localId: "s1", spaceType: "SHOP", baseValue: "", basePricePercentage: "", districtIndex: 1 },
      { localId: "s2", spaceType: "SHOP", baseValue: "", basePricePercentage: "", districtIndex: 2 },
      { localId: "s3", spaceType: "BASIC", baseValue: "", basePricePercentage: "", districtIndex: null },
    ];
    const form: BoardFormState = {
      ...emptyBoardForm(),
      spaces,
      districts: [
        { localId: "d0", name: "A", colorHex: "000000", minimumStockPercentage: "0.5000", progressionValues: {} },
        { localId: "d1", name: "B", colorHex: "111111", minimumStockPercentage: "0.5000", progressionValues: {} },
        { localId: "d2", name: "C", colorHex: "222222", minimumStockPercentage: "0.5000", progressionValues: {} },
      ],
    };

    const result = removeDistrictAt(form, 1);

    expect(result.districts.map((d) => d.name)).toEqual(["A", "C"]);
    expect(result.spaces.map((s) => s.districtIndex)).toEqual([0, null, 1, null]);
  });
});

describe("buildCreateBoardRequest", () => {
  it("trims, coerces, and shapes form state into a CreateBoardRequest", () => {
    const spaces: SpaceFormState[] = [
      { localId: "s0", spaceType: "BANK", baseValue: "", basePricePercentage: "", districtIndex: null },
      { localId: "s1", spaceType: "SHOP", baseValue: "100", basePricePercentage: ".05", districtIndex: 0 },
      { localId: "s2", spaceType: "SHOP", baseValue: "200", basePricePercentage: "0.2345", districtIndex: 0 },
    ];
    const paths: PathFormState[] = [
      { localId: "p0", from: 0, to: 1, branchOrder: "0" },
      { localId: "p1", from: 1, to: 2, branchOrder: "1" },
    ];
    const form: BoardFormState = {
      name: " My Board ",
      startingGold: "1500",
      baseSalary: "200",
      promotionBonus: "0",
      startSpaceIndex: 0,
      spaces,
      paths,
      districts: [
        {
          localId: "d0",
          name: " Downtown ",
          colorHex: "1e90ff",
          minimumStockPercentage: ".5",
          progressionValues: {
            2: { existingShopBoostPercentage: " .1 ", newShopBoostPercentage: "0.2" },
          },
        },
      ],
    };

    // basePricePercentage, minimumStockPercentage, and the boost percentages were all typed in
    // shorthand (".05", ".5", ".1", "0.2") -- buildCreateBoardRequest is where that gets padded
    // out to the exact 4-decimal-digit strings the server's BigDecimal fields require.
    expect(buildCreateBoardRequest(form)).toEqual({
      name: "My Board",
      spaces: [
        { spaceType: "BANK" },
        { spaceType: "SHOP", baseValue: 100, basePricePercentage: "0.0500", districtIndex: 0 },
        { spaceType: "SHOP", baseValue: 200, basePricePercentage: "0.2345", districtIndex: 0 },
      ],
      paths: [
        { from: 0, to: 1, branchOrder: 0 },
        { from: 1, to: 2, branchOrder: 1 },
      ],
      startSpaceIndex: 0,
      startingGold: 1500,
      baseSalary: 200,
      promotionBonus: 0,
      districts: [
        {
          name: "Downtown",
          colorHex: "1E90FF",
          minimumStockPercentage: "0.5000",
          progressions: [
            { ownedShopCount: 2, existingShopBoostPercentage: "0.1000", newShopBoostPercentage: "0.2000" },
          ],
        },
      ],
    });
  });

  it("drops a stray districtIndex on a non-SHOP space rather than passing it through", () => {
    // Shouldn't happen via the UI (the "District" field is only rendered for SHOP spaces, and
    // switching away from SHOP clears it) or survive validateBoardForm, but building the request
    // is defensive about it anyway rather than trusting every caller validated first.
    const spaces: SpaceFormState[] = [
      { localId: "s0", spaceType: "BASIC", baseValue: "", basePricePercentage: "", districtIndex: 0 },
    ];
    const form: BoardFormState = { ...emptyBoardForm(), spaces, startSpaceIndex: 0 };

    expect(buildCreateBoardRequest(form).spaces).toEqual([{ spaceType: "BASIC" }]);
  });
});

describe("validateBoardForm", () => {
  it("reports every problem with a freshly-emptied form", () => {
    const result = validateBoardForm(emptyBoardForm());

    expect(Object.keys(result.fieldErrors).sort()).toEqual(
      ["baseSalary", "name", "requiredSpaceTypes", "spaces", "startSpaceIndex"].sort(),
    );
    expect(result.errors).toHaveLength(5);
  });

  it("accepts a minimal but complete board", () => {
    const spaces: SpaceFormState[] = [
      { localId: "s0", spaceType: "BANK", baseValue: "", basePricePercentage: "", districtIndex: null },
      { localId: "s1", spaceType: "HEART", baseValue: "", basePricePercentage: "", districtIndex: null },
      { localId: "s2", spaceType: "DIAMOND", baseValue: "", basePricePercentage: "", districtIndex: null },
      { localId: "s3", spaceType: "SPADE", baseValue: "", basePricePercentage: "", districtIndex: null },
      { localId: "s4", spaceType: "CLUB", baseValue: "", basePricePercentage: "", districtIndex: null },
    ];
    const paths: PathFormState[] = [
      { localId: "p0", from: 0, to: 1, branchOrder: "0" },
      { localId: "p1", from: 1, to: 2, branchOrder: "0" },
      { localId: "p2", from: 2, to: 3, branchOrder: "0" },
      { localId: "p3", from: 3, to: 4, branchOrder: "0" },
      { localId: "p4", from: 4, to: 0, branchOrder: "0" },
    ];
    const form: BoardFormState = {
      name: "Test Board",
      startingGold: "1500",
      baseSalary: "200",
      promotionBonus: "0",
      startSpaceIndex: 0,
      spaces,
      paths,
      districts: [],
    };

    expect(validateBoardForm(form)).toEqual({ errors: [], fieldErrors: {} });
  });

  it("validates SHOP-specific fields independently of the rest of the form", () => {
    const spaces: SpaceFormState[] = [
      { localId: "s0", spaceType: "SHOP", baseValue: "", basePricePercentage: "", districtIndex: null },
      { localId: "s1", spaceType: "SHOP", baseValue: "100", basePricePercentage: "0.5000", districtIndex: 0 },
      { localId: "s2", spaceType: "BASIC", baseValue: "100", basePricePercentage: "0.5000", districtIndex: 0 },
    ];
    const form: BoardFormState = { ...emptyBoardForm(), spaces };

    const result = validateBoardForm(form);

    expect(result.fieldErrors["spaces.0.baseValue"]).toBeDefined();
    expect(result.fieldErrors["spaces.0.basePricePercentage"]).toBeDefined();
    expect(result.fieldErrors["spaces.1.baseValue"]).toBeUndefined();
    expect(result.fieldErrors["spaces.1.basePricePercentage"]).toBeUndefined();
    expect(result.fieldErrors["spaces.2.baseValue"]).toBeDefined();
    expect(result.fieldErrors["spaces.2.basePricePercentage"]).toBeDefined();
  });

  it("accepts shorthand decimal input, but rejects more than 4 decimal digits", () => {
    const spaces: SpaceFormState[] = [
      { localId: "s0", spaceType: "SHOP", baseValue: "100", basePricePercentage: ".05", districtIndex: null },
      { localId: "s1", spaceType: "SHOP", baseValue: "100", basePricePercentage: "0.05001", districtIndex: null },
    ];
    const form: BoardFormState = { ...emptyBoardForm(), spaces };

    const result = validateBoardForm(form);

    expect(result.fieldErrors["spaces.0.basePricePercentage"]).toBeUndefined();
    expect(result.fieldErrors["spaces.1.basePricePercentage"]).toBeDefined();
  });

  it("only lets a SHOP space belong to a district", () => {
    const spaces: SpaceFormState[] = [
      { localId: "s0", spaceType: "SHOP", baseValue: "100", basePricePercentage: "0.5000", districtIndex: 0 },
      { localId: "s1", spaceType: "BASIC", baseValue: "", basePricePercentage: "", districtIndex: 0 },
    ];
    const form: BoardFormState = { ...emptyBoardForm(), spaces };

    const result = validateBoardForm(form);

    expect(result.fieldErrors["spaces.0.districtIndex"]).toBeUndefined();
    expect(result.fieldErrors["spaces.1.districtIndex"]).toBe(
      "Space #1: only SHOP spaces may belong to a district.",
    );
  });
});


describe("buildGraphPreview", () => {
  it("returns nothing for an empty form", () => {
    expect(buildGraphPreview(emptyBoardForm())).toEqual({ nodes: [], edges: [] });
  });

  it("builds one node per space and skips paths with an unset from/to", () => {
    const spaces: SpaceFormState[] = [
      { localId: "s0", spaceType: "BASIC", baseValue: "", basePricePercentage: "", districtIndex: null },
      { localId: "s1", spaceType: "BASIC", baseValue: "", basePricePercentage: "", districtIndex: null },
    ];
    const paths: PathFormState[] = [
      { localId: "p0", from: 0, to: 1, branchOrder: "0" },
      { localId: "p1", from: null, to: 1, branchOrder: "0" }, // "from" not chosen yet
      { localId: "p2", from: 0, to: null, branchOrder: "0" }, // "to" not chosen yet
    ];
    const form: BoardFormState = { ...emptyBoardForm(), spaces, paths, startSpaceIndex: 0 };

    const preview = buildGraphPreview(form);

    expect(preview.nodes).toHaveLength(2);
    expect(preview.nodes[0]).toMatchObject({ id: "s0", index: 0, isStart: true });
    expect(preview.nodes[1]).toMatchObject({ id: "s1", index: 1, isStart: false });
    expect(preview.edges).toEqual([{ id: "p0", source: "s0", target: "s1", branchOrder: 0 }]);
  });

  it("colors a SHOP space by its district once the district has a valid colorHex, otherwise falls back", () => {
    const districts: DistrictFormState[] = [
      { localId: "d0", name: "Uptown", colorHex: "FF00AA", minimumStockPercentage: "", progressionValues: {} },
      { localId: "d1", name: "Midtown", colorHex: "not-a-color", minimumStockPercentage: "", progressionValues: {} },
    ];
    const spaces: SpaceFormState[] = [
      { localId: "s0", spaceType: "SHOP", baseValue: "", basePricePercentage: "", districtIndex: 0 },
      { localId: "s1", spaceType: "SHOP", baseValue: "", basePricePercentage: "", districtIndex: 1 },
      { localId: "s2", spaceType: "BASIC", baseValue: "", basePricePercentage: "", districtIndex: null },
    ];
    const form: BoardFormState = { ...emptyBoardForm(), spaces, districts };

    const preview = buildGraphPreview(form);

    expect(preview.nodes[0]).toMatchObject({ color: "#FF00AA", districtName: "Uptown" });
    // Midtown's colorHex isn't valid yet, but the district assignment itself is real -- the name
    // still shows, only the swatch falls back to the space type's default color.
    expect(preview.nodes[1]).toMatchObject({ districtName: "Midtown" });
    expect(preview.nodes[1].color).not.toBe("#not-a-color");
    expect(preview.nodes[2].districtName).toBeUndefined();
  });
});
