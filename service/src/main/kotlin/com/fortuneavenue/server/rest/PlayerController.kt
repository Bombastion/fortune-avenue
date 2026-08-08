package com.fortuneavenue.server.rest

import com.fortuneavenue.server.models.common.rest.ErrorResponse
import com.fortuneavenue.server.models.player.rest.AddPlayerRequest
import com.fortuneavenue.server.models.player.rest.PlayerResponse
import com.fortuneavenue.server.models.player.rest.toResponse
import com.fortuneavenue.server.service.GameNotFoundException
import com.fortuneavenue.server.service.PlayerService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.uuid.Uuid

/**
 * Nested under /games/{gameId} rather than a top-level /players -- a player
 * only ever makes sense in the context of a specific game.
 */
@RestController
@RequestMapping("/games/{gameId}/players")
class PlayerController(
	private val playerService: PlayerService,
) {

	@PostMapping
	fun addPlayer(
		@PathVariable gameId: String,
		@RequestBody request: AddPlayerRequest,
	): ResponseEntity<Any> {
		val parsedGameId = Uuid.parseOrNull(gameId) ?: return ResponseEntity.badRequest().build()

		val userId = request.userId?.let {
			Uuid.parseOrNull(it)
				?: return ResponseEntity.badRequest().body<Any>(ErrorResponse("userId is not a valid id."))
		}

		val result = playerService.addPlayer(parsedGameId, userId)

		return result.fold(
			onSuccess = { player -> ResponseEntity.status(HttpStatus.CREATED).body<Any>(player.toResponse()) },
			onFailure = { error ->
				when (error) {
					is GameNotFoundException -> ResponseEntity.notFound().build()
					else -> ResponseEntity.badRequest().body<Any>(ErrorResponse(error.message ?: "Invalid player"))
				}
			},
		)
	}

	@GetMapping
	fun getPlayers(@PathVariable gameId: String): ResponseEntity<List<PlayerResponse>> {
		val parsedGameId = Uuid.parseOrNull(gameId) ?: return ResponseEntity.badRequest().build()

		val players = playerService.getPlayers(parsedGameId) ?: return ResponseEntity.notFound().build()

		return ResponseEntity.ok(players.map { it.toResponse() })
	}
}
