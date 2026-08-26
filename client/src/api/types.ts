// TypeScript mirrors of the server's REST DTOs (see
// service/src/main/kotlin/com/fortuneavenue/server/models/**/rest/*.kt).
//
// A note on decimal fields (basePricePercentage, minimumStockPercentage,
// existingShopBoostPercentage, newShopBoostPercentage): the server deserializes these into
// java.math.BigDecimal and cares about their exact scale (it requires exactly 4 digits after the
// decimal point, e.g. "0.5000" not "0.5"). A plain JS `number` can't preserve trailing zeros
// (JSON.stringify(0.5) is "0.5", not "0.5000"), so on the request side these are typed as
// `string` -- the exact text the user typed, validated to have exactly 4 digits -- and serialized
// as unquoted numeric literals by api/json.ts. On the response side the server sends back a plain
// JSON number, so those are typed as `number`.

export const SPACE_TYPES = [
  "BASIC",
  "SHOP",
  "HEART",
  "DIAMOND",
  "SPADE",
  "CLUB",
  "BANK",
] as const;

export type SpaceType = (typeof SPACE_TYPES)[number];

export const SUIT_SPACE_TYPES: SpaceType[] = ["HEART", "DIAMOND", "SPADE", "CLUB"];

export type SortDirection = "ASC" | "DESC";

// ---- Board creation request ----

export interface CreateBoardSpaceRequest {
  spaceType: SpaceType;
  baseValue?: number;
  basePricePercentage?: string;
  districtIndex?: number;
}

export interface CreateBoardPathRequest {
  from: number;
  to: number;
  branchOrder: number;
}

export interface CreateDistrictProgressionRequest {
  ownedShopCount: number;
  existingShopBoostPercentage: string;
  newShopBoostPercentage: string;
}

export interface CreateDistrictRequest {
  name: string;
  colorHex: string;
  minimumStockPercentage: string;
  progressions: CreateDistrictProgressionRequest[];
}

export interface CreateBoardRequest {
  name: string;
  spaces: CreateBoardSpaceRequest[];
  paths: CreateBoardPathRequest[];
  startSpaceIndex: number;
  startingGold: number;
  baseSalary: number;
  promotionBonus: number;
  districts: CreateDistrictRequest[];
}

// ---- Board response ----

export interface BoardSpaceResponse {
  id: string;
  spaceType: SpaceType;
  baseValue?: number;
  basePricePercentage?: number;
  districtId?: string;
}

export interface BoardPathResponse {
  from: string;
  to: string;
  branchOrder: number;
}

export interface DistrictProgressionResponse {
  ownedShopCount: number;
  existingShopBoostPercentage: number;
  newShopBoostPercentage: number;
}

export interface DistrictResponse {
  id: string;
  name: string;
  colorHex: string;
  minimumStockPercentage: number;
  progressions: DistrictProgressionResponse[];
}

export interface BoardResponse {
  id: string;
  name: string;
  startSpaceId: string;
  startingGold: number;
  baseSalary: number;
  promotionBonus: number;
  spaces: BoardSpaceResponse[];
  paths: BoardPathResponse[];
  districts: DistrictResponse[];
}

export interface Page<T> {
  items: T[];
  page: number;
  pageSize: number;
  direction: SortDirection;
  totalPages: number;
}

// ---- Games ----

export interface CreateGameRequest {
  boardId: string;
  targetNetWorth?: number;
}

export interface GameResponse {
  id: string;
  boardId: string;
  targetNetWorth: number;
}

// ---- Players ----

export interface AddPlayerRequest {
  userId?: string;
}

export interface PlayerResponse {
  id: string;
  gameId: string;
  userId?: string;
}

// ---- Users ----

export interface CreateUserRequest {
  username: string;
}

export interface UserResponse {
  id: string;
  username: string;
}

// ---- Errors ----

export interface ErrorResponse {
  message: string;
}
