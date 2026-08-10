package com.fortuneavenue.server.service

import com.fortuneavenue.server.dao.BoardDao
import com.fortuneavenue.server.models.board.db.BoardGraph
import com.fortuneavenue.server.models.board.rest.CreateBoardRequest
import com.fortuneavenue.server.models.common.rest.Page
import com.fortuneavenue.server.models.common.rest.SortDirection
import org.springframework.stereotype.Service
import kotlin.uuid.Uuid

@Service
class BoardService(
	private val boardDao: BoardDao,
) {

	fun createBoard(request: CreateBoardRequest): Result<BoardGraph> {
		val edges = request.paths.map { BoardGraphValidator.Edge(from = it.from, to = it.to) }

		val errors = BoardGraphValidator.validate(
			spaceCount = request.spaces.size,
			edges = edges,
			start = request.startSpaceIndex,
		)

		if (errors.isNotEmpty()) {
			return Result.failure(InvalidBoardException(errors.joinToString(" ")))
		}

		val graph = boardDao.create(
			name = request.name,
			spaceInputs = request.spaces.map { BoardDao.SpaceInput(it.spaceType) },
			pathInputs = request.paths.map { BoardDao.PathInput(it.from, it.to, it.branchOrder) },
			startIndex = request.startSpaceIndex,
		)

		return Result.success(graph)
	}

	fun getBoard(id: Uuid): BoardGraph? = boardDao.findById(id)

	/** [page] is zero-indexed. */
	fun listBoards(page: Int, pageSize: Int, direction: SortDirection = SortDirection.ASC): Result<Page<BoardGraph>> {
		if (page < 0) {
			return Result.failure(InvalidBoardException("page must be zero or greater."))
		}
		if (pageSize < 1) {
			return Result.failure(InvalidBoardException("pageSize must be at least 1."))
		}

		val items = boardDao.findPage(page = page, pageSize = pageSize, ascending = direction == SortDirection.ASC)
		val totalItems = boardDao.count()

		return Result.success(Page.of(items = items, page = page, pageSize = pageSize, direction = direction, totalItems = totalItems))
	}
}
