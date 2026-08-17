package com.fortuneavenue.server.service

import com.fortuneavenue.server.models.board.db.SpaceType
import com.fortuneavenue.server.models.board.rest.CreateBoardSpaceRequest

/**
 * Validates that a board creation request's spaces include at least one BANK space and at least one
 * of each suit -- HEART, DIAMOND, SPADE, CLUB (see SpaceType) -- so a game can never be started on
 * a board where the BANK promotion (see GameSimulationService) is impossible: nothing to trigger
 * it, or a suit a player could never actually complete the set with.
 */
object RequiredSpaceTypesValidator {

    private val REQUIRED_SPACE_TYPES =
        listOf(SpaceType.BANK, SpaceType.HEART, SpaceType.DIAMOND, SpaceType.SPADE, SpaceType.CLUB)

    fun validate(spaces: List<CreateBoardSpaceRequest>): List<String> {
        val presentTypes = spaces.map { it.spaceType }.toSet()
        val missingTypes = REQUIRED_SPACE_TYPES.filter { it !in presentTypes }

        return if (missingTypes.isEmpty()) {
            emptyList()
        } else {
            listOf(
                "A board must include at least one space of each of $REQUIRED_SPACE_TYPES; missing $missingTypes."
            )
        }
    }
}
