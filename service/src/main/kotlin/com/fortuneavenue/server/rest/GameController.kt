package com.fortuneavenue.server.rest

import com.fortuneavenue.server.models.common.rest.ErrorResponse
import com.fortuneavenue.server.models.game.rest.CreateGameRequest
import com.fortuneavenue.server.models.game.rest.GameResponse
import com.fortuneavenue.server.models.game.rest.toResponse
import com.fortuneavenue.server.service.GameService
import kotlin.uuid.Uuid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/games")
class GameController(private val gameService: GameService) {

    @PostMapping
    fun createGame(@RequestBody request: CreateGameRequest): ResponseEntity<Any> {
        val boardId =
            Uuid.parseOrNull(request.boardId)
                ?: return ResponseEntity.badRequest()
                    .body<Any>(ErrorResponse("boardId is not a valid id."))

        val result = gameService.createGame(boardId, request.targetNetWorth, request.maxTurns)

        return result.fold(
            onSuccess = { game ->
                ResponseEntity.status(HttpStatus.CREATED).body<Any>(game.toResponse())
            },
            onFailure = { error ->
                ResponseEntity.badRequest()
                    .body<Any>(ErrorResponse(error.message ?: "Invalid game"))
            },
        )
    }

    @GetMapping("/{id}")
    fun getGame(@PathVariable id: String): ResponseEntity<GameResponse> {
        val gameId = Uuid.parseOrNull(id) ?: return ResponseEntity.badRequest().build()

        val game = gameService.getGame(gameId) ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(game.toResponse())
    }
}
