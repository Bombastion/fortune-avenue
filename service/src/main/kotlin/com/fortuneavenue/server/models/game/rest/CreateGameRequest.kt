package com.fortuneavenue.server.models.game.rest

/**
 * [targetNetWorth], if given, must be a positive integer -- see
 * [com.fortuneavenue.server.service.GameService]. The game ends the moment any player's net worth
 * (gold on hand, plus every shop they own, plus the current value of every stock they hold -- see
 * [com.fortuneavenue.server.service.GameSimulationService]) reaches or exceeds it, exactly as it
 * already does once turnNumber reaches maxTurns. Omit to have
 * [com.fortuneavenue.server.service.GameService.createGame] default it to 6000 gold.
 */
data class CreateGameRequest(val boardId: String, val targetNetWorth: Int? = null)
