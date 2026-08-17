package com.fortuneavenue.server.models.player.db

import com.fortuneavenue.server.models.board.db.BoardSpacesTable
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.dao.id.UuidTable

// Deliberately minimal beyond position for now -- money, owned properties,
// items, etc. all land here later as their own columns rather than on
// PlayersTable
object PlayerStatesTable : UuidTable("player_states") {
    val playerId = reference("player_id", PlayersTable).uniqueIndex()

    // Nullable: a freshly created player hasn't been placed on the board yet
    // (no start-of-game logic exists to do that placement yet either).
    val currentSpaceId = optReference("current_space_id", BoardSpacesTable)

    val status =
        enumerationByName("status", STATUS_LENGTH, PlayerStatus::class)
            .default(PlayerStatus.WAITING)

    // Seeded from the game's board.startingGold when the player is created (see PlayerDao); free
    // to rise and fall from there over the course of play. Can go negative; that means the
    // player has spent more than they have on hand and owes an auction of their properties to get
    // back in the positive (auction mechanics aren't implemented yet).
    val currentGold = integer("current_gold")

    // Card suits (SpaceType.HEART/DIAMOND/SPADE/CLUB, stored by name) this player has picked up
    // by passing or landing on that type of space over the course of the game
    val heldSuits =
        array<String>("held_suits", VarCharColumnType(SPACE_TYPE_LENGTH)).default(emptyList())

    // How many times this player has collected the BANK promotion so far (see
    // GameSimulationService) -- starts at 0, and is used as-is as the "level" in that payout's
    // formula before being incremented for next time.
    val promotionCount = integer("promotion_count").default(0)
}

private const val STATUS_LENGTH = 50

// Matches SpaceType's own VARCHAR length (see BoardSpacesTable) -- held_suits stores those same
// enum names.
private const val SPACE_TYPE_LENGTH = 50
