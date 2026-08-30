import type {
  AddPlayerRequest,
  BoardResponse,
  CreateGameRequest,
  CreateUserRequest,
  ErrorResponse,
  GameResponse,
  Page,
  PlayerResponse,
  SortDirection,
  UserResponse,
} from "./types";

/**
 * Thrown for any non-2xx response. [message] is the server's [ErrorResponse.message] when the
 * body parsed as one, otherwise a generic fallback -- callers show this directly next to the form
 * that triggered the request.
 */
export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

/** Thrown when the request never reached the server at all (offline, proxy down, CORS, etc). */
export class NetworkError extends Error {
  // Declared explicitly (rather than relying on the built-in Error.cause) since that typing
  // isn't available until the ES2022 lib, and this project targets ES2020.
  constructor(public readonly originalError: unknown) {
    super("Could not reach the server. Check your connection and try again.");
    this.name = "NetworkError";
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      ...init,
      headers: { "Content-Type": "application/json", ...(init?.headers ?? {}) },
    });
  } catch (cause) {
    throw new NetworkError(cause);
  }

  if (!response.ok) {
    let message = `Request failed with status ${response.status}.`;
    try {
      const body = (await response.json()) as Partial<ErrorResponse>;
      if (body?.message) message = body.message;
    } catch {
      // Response body wasn't JSON (or was empty) -- fall back to the generic message above.
    }
    throw new ApiError(message, response.status);
  }

  if (response.status === 204) return undefined as T;

  return (await response.json()) as T;
}

export const api = {
  // ---- Users ----
  createUser: (body: CreateUserRequest) =>
    request<UserResponse>("/users", { method: "POST", body: JSON.stringify(body) }),

  getUser: (id: string) => request<UserResponse>(`/users/${encodeURIComponent(id)}`),

  listUsers: (page: number, pageSize: number, direction: SortDirection) =>
    request<Page<UserResponse>>(`/users?page=${page}&pageSize=${pageSize}&direction=${direction}`),

  // ---- Boards ----
  /**
   * [request] is pre-serialized (see api/json.ts) rather than a plain object, so the caller keeps
   * control over how the decimal fields are encoded.
   */
  createBoard: (requestJson: string) =>
    request<BoardResponse>("/boards", { method: "POST", body: requestJson }),

  listBoards: (page: number, pageSize: number, direction: SortDirection) =>
    request<Page<BoardResponse>>(
      `/boards?page=${page}&pageSize=${pageSize}&direction=${direction}`,
    ),

  getBoard: (id: string) => request<BoardResponse>(`/boards/${encodeURIComponent(id)}`),

  // ---- Games ----
  createGame: (body: CreateGameRequest) =>
    request<GameResponse>("/games", { method: "POST", body: JSON.stringify(body) }),

  getGame: (id: string) => request<GameResponse>(`/games/${encodeURIComponent(id)}`),

  // ---- Players ----
  addPlayer: (gameId: string, body: AddPlayerRequest) =>
    request<PlayerResponse>(`/games/${encodeURIComponent(gameId)}/players`, {
      method: "POST",
      body: JSON.stringify(body),
    }),

  getPlayers: (gameId: string) =>
    request<PlayerResponse[]>(`/games/${encodeURIComponent(gameId)}/players`),
};
