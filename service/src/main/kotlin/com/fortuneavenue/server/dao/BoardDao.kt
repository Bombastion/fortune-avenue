package com.fortuneavenue.server.dao

import com.fortuneavenue.server.models.board.db.Board
import com.fortuneavenue.server.models.board.db.BoardGraph
import com.fortuneavenue.server.models.board.db.BoardPath
import com.fortuneavenue.server.models.board.db.BoardPathsTable
import com.fortuneavenue.server.models.board.db.BoardSpace
import com.fortuneavenue.server.models.board.db.BoardSpacesTable
import com.fortuneavenue.server.models.board.db.SpaceType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository
import kotlin.uuid.Uuid

@Repository
class BoardDao {

	data class SpaceInput(val spaceType: SpaceType)
	data class PathInput(val fromIndex: Int, val toIndex: Int, val branchOrder: Int)

	fun create(
		name: String,
		spaceInputs: List<SpaceInput>,
		pathInputs: List<PathInput>,
		startIndex: Int,
	): BoardGraph = transaction {
		// start_space_id is a plain uuid column, not a typed reference() (see the
		// comment on BoardsTable), but Board requires it to exist. We do some
		// wonky stuff with flushing to make sure the space exists, then update
		// the board.
		val board = Board.new { this.name = name }
		board.flush()

		val spaces = spaceInputs.map { input ->
			BoardSpace.new {
				boardId = board.id
				spaceType = input.spaceType
			}
		}
		spaces.firstOrNull()?.flush()

		board.startSpaceId = spaces[startIndex].id.value
		board.flush()

		val paths = pathInputs.map { input ->
			BoardPath.new {
				boardId = board.id
				fromSpaceId = spaces[input.fromIndex].id
				toSpaceId = spaces[input.toIndex].id
				branchOrder = input.branchOrder
			}
		}

		BoardGraph(board = board, spaces = spaces, paths = paths)
	}

	fun findById(id: Uuid): BoardGraph? = transaction {
		val board = Board.findById(id) ?: return@transaction null
		val spaces = BoardSpace.find { BoardSpacesTable.boardId eq board.id }.toList()
		val paths = BoardPath.find { BoardPathsTable.boardId eq board.id }.toList()

		BoardGraph(board = board, spaces = spaces, paths = paths)
	}
}
