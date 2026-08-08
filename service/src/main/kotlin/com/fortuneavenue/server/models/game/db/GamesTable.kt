package com.fortuneavenue.server.models.game.db

import org.jetbrains.exposed.v1.core.dao.id.UuidTable

// Deliberately minimal for now -- just enough of a real table for players to
// reference. Whatever a game actually needs (board, turn state, status, ...)
// lands here once that's built.
object GamesTable : UuidTable("games")
