package com.fortuneavenue.server.rest

import com.fortuneavenue.server.models.board.rest.BoardResponse
import com.fortuneavenue.server.models.board.rest.CreateBoardRequest
import com.fortuneavenue.server.models.board.rest.toResponse
import com.fortuneavenue.server.models.common.rest.ErrorResponse
import com.fortuneavenue.server.service.BoardService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.uuid.Uuid

@RestController
@RequestMapping("/boards")
class BoardController(
	private val boardService: BoardService,
) {

	@PostMapping
	fun createBoard(@RequestBody request: CreateBoardRequest): ResponseEntity<Any> {
		val result = boardService.createBoard(request)

		return result.fold(
			onSuccess = { graph -> ResponseEntity.status(HttpStatus.CREATED).body<Any>(graph.toResponse()) },
			onFailure = { error ->
				ResponseEntity.badRequest().body<Any>(ErrorResponse(error.message ?: "Invalid board"))
			},
		)
	}

	@GetMapping("/{id}")
	fun getBoard(@PathVariable id: String): ResponseEntity<BoardResponse> {
		val boardId = Uuid.parseOrNull(id) ?: return ResponseEntity.badRequest().build()

		val graph = boardService.getBoard(boardId) ?: return ResponseEntity.notFound().build()

		return ResponseEntity.ok(graph.toResponse())
	}
}
