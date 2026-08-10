package com.fortuneavenue.server.service

/** The game hasn't started, is already over, or it's some other player's turn. */
class InvalidTurnException(message: String) : RuntimeException(message)
