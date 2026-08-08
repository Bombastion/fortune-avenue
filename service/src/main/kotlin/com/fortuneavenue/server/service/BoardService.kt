package com.fortuneavenue.server.service

import com.fortuneavenue.server.dao.BoardDao
import com.fortuneavenue.server.models.board.db.BoardGraph
import com.fortuneavenue.server.models.board.rest.CreateBoardRequest
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
}
