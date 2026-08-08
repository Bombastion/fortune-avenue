package com.fortuneavenue.server.service

/**
 * Thrown when an operation is scoped to a game (by id) that doesn't exist.
 * Kept distinct from InvalidPlayerException/InvalidGameException so callers
 * -- REST controllers today, anything else later -- can tell "the thing you
 * pointed at doesn't exist" (404-shaped) apart from "what you're trying to
 * create/change isn't valid" (400-shaped) without re-deriving that
 * distinction themselves.
 */
class GameNotFoundException(message: String) : RuntimeException(message)
