// Form state shapes for BoardCreatePage, plus the pure functions that turn that state into a
// validated CreateBoardRequest. Kept separate from the component so the (fairly involved)
// validation and referential-integrity logic can be read and reasoned about on its own.
import type {
  CreateBoardPathRequest,
  CreateBoardRequest,
  CreateBoardSpaceRequest,
  CreateDistrictProgressionRequest,
  CreateDistrictRequest,
  SpaceType,
} from "../api/types";
import { SUIT_SPACE_TYPES } from "../api/types";
import { validateBoardGraph } from "../validation/boardGraph";
import {
  isBlank,
  isFourDigitFractionStrictlyBetweenZeroAndOne,
  isHexColor,
  isNonNegativeIntegerString,
  isPositiveFourDigitDecimalString,
  isPositiveIntegerString,
} from "../validation/rules";
import { newLocalId } from "../utils/id";

const MIN_SPACES_REQUIRING_PROGRESSIONS = 2;

export interface SpaceFormState {
  localId: string;
  spaceType: SpaceType;
  baseValue: string;
  basePricePercentage: string;
  districtIndex: number | null;
}

export interface PathFormState {
  localId: string;
  from: number | null;
  to: number | null;
  branchOrder: string;
}

export interface DistrictFormState {
  localId: string;
  name: string;
  colorHex: string;
  minimumStockPercentage: string;
  /** Keyed by ownedShopCount. Rows are derived from how many spaces point at this district (see
   * requiredProgressionLevels below) -- entries for levels no longer required are kept around
   * (not deleted) so the values aren't lost if the user temporarily reassigns a space away and
   * back. */
  progressionValues: Record<number, { existingShopBoostPercentage: string; newShopBoostPercentage: string }>;
}

export interface BoardFormState {
  name: string;
  startingGold: string;
  baseSalary: string;
  promotionBonus: string;
  startSpaceIndex: number | null;
  spaces: SpaceFormState[];
  paths: PathFormState[];
  districts: DistrictFormState[];
}

export function newSpace(): SpaceFormState {
  return {
    localId: newLocalId(),
    spaceType: "BASIC",
    baseValue: "",
    basePricePercentage: "",
    districtIndex: null,
  };
}

export function newPath(): PathFormState {
  return { localId: newLocalId(), from: null, to: null, branchOrder: "0" };
}

export function newDistrict(): DistrictFormState {
  return {
    localId: newLocalId(),
    name: "",
    colorHex: "",
    minimumStockPercentage: "",
    progressionValues: {},
  };
}

export function emptyBoardForm(): BoardFormState {
  return {
    name: "",
    startingGold: "1500",
    baseSalary: "",
    promotionBonus: "0",
    startSpaceIndex: null,
    spaces: [],
    paths: [],
    districts: [],
  };
}

/** How many spaces currently point at district [districtIndex]. */
export function spaceCountForDistrict(spaces: SpaceFormState[], districtIndex: number): number {
  return spaces.filter((s) => s.districtIndex === districtIndex).length;
}

/** Mirrors DistrictProgressionValidator.kt: 2..spaceCount once a district has >= 2 spaces. */
export function requiredProgressionLevels(spaceCount: number): number[] {
  if (spaceCount < MIN_SPACES_REQUIRING_PROGRESSIONS) return [];
  const levels: number[] = [];
  for (let level = MIN_SPACES_REQUIRING_PROGRESSIONS; level <= spaceCount; level++) {
    levels.push(level);
  }
  return levels;
}

// ---- Referential-integrity helpers: removing a space/district can invalidate indices that other
// parts of the form point at (a path's from/to, a space's districtIndex, the start space). These
// keep the form's own indices consistent immediately after a removal, the same way the server
// would reject an out-of-range index if we let one through. ----

export function removeSpaceAt(form: BoardFormState, index: number): BoardFormState {
  const spaces = form.spaces.filter((_, i) => i !== index);

  const paths = form.paths
    .filter((p) => p.from !== index && p.to !== index)
    .map((p) => ({
      ...p,
      from: p.from !== null && p.from > index ? p.from - 1 : p.from,
      to: p.to !== null && p.to > index ? p.to - 1 : p.to,
    }));

  const startSpaceIndex =
    form.startSpaceIndex === index
      ? null
      : form.startSpaceIndex !== null && form.startSpaceIndex > index
        ? form.startSpaceIndex - 1
        : form.startSpaceIndex;

  return { ...form, spaces, paths, startSpaceIndex };
}

export function removeDistrictAt(form: BoardFormState, index: number): BoardFormState {
  const districts = form.districts.filter((_, i) => i !== index);
  const spaces = form.spaces.map((s) => ({
    ...s,
    districtIndex:
      s.districtIndex === index
        ? null
        : s.districtIndex !== null && s.districtIndex > index
          ? s.districtIndex - 1
          : s.districtIndex,
  }));

  return { ...form, districts, spaces };
}

// ---- Validation ----

export interface BoardValidationResult {
  /** Every problem, for the top-of-form summary. */
  errors: string[];
  /** The same problems, keyed for inline display next to the field that caused them. */
  fieldErrors: Record<string, string>;
}

function addError(result: BoardValidationResult, key: string | null, message: string) {
  result.errors.push(message);
  if (key) result.fieldErrors[key] = message;
}

export function validateBoardForm(form: BoardFormState): BoardValidationResult {
  const result: BoardValidationResult = { errors: [], fieldErrors: {} };

  if (isBlank(form.name)) {
    addError(result, "name", "Board name is required.");
  }

  if (!isPositiveIntegerString(form.startingGold)) {
    addError(result, "startingGold", "Starting gold must be a positive whole number.");
  }
  if (!isPositiveIntegerString(form.baseSalary)) {
    addError(result, "baseSalary", "Base salary must be a positive whole number.");
  }
  if (!isNonNegativeIntegerString(form.promotionBonus)) {
    addError(result, "promotionBonus", "Promotion bonus must be zero or a positive whole number.");
  }

  if (form.spaces.length === 0) {
    addError(result, "spaces", "A board needs at least one space.");
  }

  // Every space of each required type -- mirrors RequiredSpaceTypesValidator.kt.
  const presentTypes = new Set(form.spaces.map((s) => s.spaceType));
  const requiredTypes: SpaceType[] = ["BANK", ...SUIT_SPACE_TYPES];
  const missingTypes = requiredTypes.filter((t) => !presentTypes.has(t));
  if (missingTypes.length > 0) {
    addError(
      result,
      "requiredSpaceTypes",
      `The board must include at least one space of each type: ${requiredTypes.join(", ")}. Missing: ${missingTypes.join(", ")}.`,
    );
  }

  // Per-space validation -- mirrors ShopSpaceValidator.kt.
  form.spaces.forEach((space, index) => {
    if (space.spaceType === "SHOP") {
      if (!isPositiveIntegerString(space.baseValue)) {
        addError(result, `spaces.${index}.baseValue`, `Space #${index}: a SHOP space needs a positive baseValue.`);
      }
      if (!isFourDigitFractionStrictlyBetweenZeroAndOne(space.basePricePercentage)) {
        addError(
          result,
          `spaces.${index}.basePricePercentage`,
          `Space #${index}: a SHOP space needs a basePricePercentage strictly between 0 and 1, with exactly 4 digits (e.g. 0.1234).`,
        );
      }
    } else {
      if (!isBlank(space.baseValue)) {
        addError(result, `spaces.${index}.baseValue`, `Space #${index}: only SHOP spaces have a baseValue.`);
      }
      if (!isBlank(space.basePricePercentage)) {
        addError(
          result,
          `spaces.${index}.basePricePercentage`,
          `Space #${index}: only SHOP spaces have a basePricePercentage.`,
        );
      }
    }
  });

  // Paths -- from/to must be chosen and reference real spaces (the dropdown already constrains
  // this, but a freshly-added path starts as "unset").
  form.paths.forEach((path, index) => {
    if (path.from === null) {
      addError(result, `paths.${index}.from`, `Path #${index}: choose a "from" space.`);
    }
    if (path.to === null) {
      addError(result, `paths.${index}.to`, `Path #${index}: choose a "to" space.`);
    }
    if (!isNonNegativeIntegerString(path.branchOrder)) {
      addError(result, `paths.${index}.branchOrder`, `Path #${index}: branch order must be zero or a positive whole number.`);
    }
  });

  // Districts -- mirrors DistrictValidator.kt + DistrictProgressionValidator.kt.
  form.districts.forEach((district, index) => {
    if (isBlank(district.name)) {
      addError(result, `districts.${index}.name`, `District #${index}: name is required.`);
    }
    if (!isHexColor(district.colorHex)) {
      addError(
        result,
        `districts.${index}.colorHex`,
        `District #${index}: colorHex must be exactly 6 hex characters (0-9, A-F).`,
      );
    }
    if (!isFourDigitFractionStrictlyBetweenZeroAndOne(district.minimumStockPercentage)) {
      addError(
        result,
        `districts.${index}.minimumStockPercentage`,
        `District #${index}: minimumStockPercentage must be strictly between 0 and 1, with exactly 4 digits (e.g. 0.5000).`,
      );
    }

    const spaceCount = spaceCountForDistrict(form.spaces, index);
    const levels = requiredProgressionLevels(spaceCount);
    for (const level of levels) {
      const values = district.progressionValues[level];
      if (!values || !isPositiveFourDigitDecimalString(values.existingShopBoostPercentage)) {
        addError(
          result,
          `districts.${index}.progression.${level}.existing`,
          `District #${index}, ownedShopCount ${level}: existingShopBoostPercentage must be a positive value with exactly 4 digits.`,
        );
      }
      if (!values || !isPositiveFourDigitDecimalString(values.newShopBoostPercentage)) {
        addError(
          result,
          `districts.${index}.progression.${level}.new`,
          `District #${index}, ownedShopCount ${level}: newShopBoostPercentage must be a positive value with exactly 4 digits.`,
        );
      }
    }
  });

  // Start space + graph shape -- mirrors BoardGraphValidator.kt.
  if (form.startSpaceIndex === null) {
    addError(result, "startSpaceIndex", "Choose a start space.");
  } else if (form.spaces.length > 0) {
    const edges = form.paths
      .filter((p) => p.from !== null && p.to !== null)
      .map((p) => ({ from: p.from as number, to: p.to as number }));
    const graphErrors = validateBoardGraph(form.spaces.length, edges, form.startSpaceIndex);
    for (const message of graphErrors) {
      addError(result, "graph", message);
    }
  }

  return result;
}

// ---- Request building (only call once validateBoardForm reports no errors) ----

export function buildCreateBoardRequest(form: BoardFormState): CreateBoardRequest {
  const spaces: CreateBoardSpaceRequest[] = form.spaces.map((space) => ({
    spaceType: space.spaceType,
    baseValue: space.spaceType === "SHOP" ? Number(space.baseValue) : undefined,
    basePricePercentage: space.spaceType === "SHOP" ? space.basePricePercentage.trim() : undefined,
    districtIndex: space.districtIndex ?? undefined,
  }));

  const paths: CreateBoardPathRequest[] = form.paths.map((path) => ({
    from: path.from as number,
    to: path.to as number,
    branchOrder: Number(path.branchOrder),
  }));

  const districts: CreateDistrictRequest[] = form.districts.map((district, index) => {
    const spaceCount = spaceCountForDistrict(form.spaces, index);
    const levels = requiredProgressionLevels(spaceCount);
    const progressions: CreateDistrictProgressionRequest[] = levels.map((level) => {
      const values = district.progressionValues[level];
      return {
        ownedShopCount: level,
        existingShopBoostPercentage: values.existingShopBoostPercentage.trim(),
        newShopBoostPercentage: values.newShopBoostPercentage.trim(),
      };
    });

    return {
      name: district.name.trim(),
      colorHex: district.colorHex.trim().toUpperCase(),
      minimumStockPercentage: district.minimumStockPercentage.trim(),
      progressions,
    };
  });

  return {
    name: form.name.trim(),
    spaces,
    paths,
    startSpaceIndex: form.startSpaceIndex as number,
    startingGold: Number(form.startingGold),
    baseSalary: Number(form.baseSalary),
    promotionBonus: Number(form.promotionBonus),
    districts,
  };
}
