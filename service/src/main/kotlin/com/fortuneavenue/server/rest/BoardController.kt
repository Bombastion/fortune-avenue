package com.fortuneavenue.server.rest

import com.fortuneavenue.server.models.board.rest.BoardResponse
import com.fortuneavenue.server.models.board.rest.CreateBoardRequest
import com.fortuneavenue.server.models.board.rest.toResponse
import com.fortuneavenue.server.models.common.rest.ErrorResponse
import com.fortuneavenue.server.models.common.rest.SortDirection
import com.fortuneavenue.server.models.common.rest.map
import com.fortuneavenue.server.service.BoardService
import kotlin.uuid.Uuid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/boards")
class BoardController(private val boardService: BoardService) {

    @PostMapping
    fun createBoard(@RequestBody request: CreateBoardRequest): ResponseEntity<Any> {
        val result = boardService.createBoard(request)

        return result.fold(
            onSuccess = { graph ->
                ResponseEntity.status(HttpStatus.CREATED).body<Any>(graph.toResponse())
            },
            onFailure = { error ->
                ResponseEntity.badRequest()
                    .body<Any>(ErrorResponse(error.message ?: "Invalid board"))
            },
        )
    }

    @GetMapping
    fun listBoards(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
        @RequestParam(defaultValue = "ASC") direction: String,
    ): ResponseEntity<Any> {
        val sortDirection =
            SortDirection.entries.firstOrNull { it.name == direction }
                ?: return ResponseEntity.badRequest()
                    .body<Any>(
                        ErrorResponse(
                            "direction must be one of ${SortDirection.entries.map { it.name }}."
                        )
                    )

        val result =
            boardService.listBoards(page = page, pageSize = pageSize, direction = sortDirection)

        return result.fold(
            onSuccess = { boardsPage ->
                ResponseEntity.ok<Any>(boardsPage.map { it.toResponse() })
            },
            onFailure = { error ->
                ResponseEntity.badRequest()
                    .body<Any>(ErrorResponse(error.message ?: "Invalid pagination request."))
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
