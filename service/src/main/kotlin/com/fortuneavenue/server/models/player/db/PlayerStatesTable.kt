package com.fortuneavenue.server.models.player.db

import com.fortuneavenue.server.models.board.db.BoardSpacesTable
import org.jetbrains.exposed.v1.core.dao.id.UuidTable

// Deliberately minimal beyond position for now -- money, owned properties,
// items, etc. all land here later as their own columns rather than on
// PlayersTable
object PlayerStatesTable : UuidTable("player_states") {
	val playerId = reference("player_id", PlayersTable).uniqueIndex()

	// Nullable: a freshly created player hasn't been placed on the board yet
	// (no start-of-game logic exists to do that placement yet either).
	val currentSpaceId = optReference("current_space_id", BoardSpacesTable)

	val status = enumerationByName("status", STATUS_LENGTH, PlayerStatus::class).default(PlayerStatus.WAITING)
}

private const val STATUS_LENGTH = 50
