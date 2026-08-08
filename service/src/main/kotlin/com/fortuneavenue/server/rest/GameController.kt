package com.fortuneavenue.server.rest

import com.fortuneavenue.server.models.game.rest.GameResponse
import com.fortuneavenue.server.models.game.rest.toResponse
import com.fortuneavenue.server.service.GameService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.uuid.Uuid

@RestController
@RequestMapping("/games")
class GameController(
	private val gameService: GameService,
) {

	@PostMapping
	fun createGame(): ResponseEntity<GameResponse> {
		val game = gameService.createGame()

		return ResponseEntity.status(HttpStatus.CREATED).body(game.toResponse())
	}

	@GetMapping("/{id}")
	fun getGame(@PathVariable id: String): ResponseEntity<GameResponse> {
		val gameId = Uuid.parseOrNull(id) ?: return ResponseEntity.badRequest().build()

		val game = gameService.getGame(gameId) ?: return ResponseEntity.notFound().build()

		return ResponseEntity.ok(game.toResponse())
	}
}
