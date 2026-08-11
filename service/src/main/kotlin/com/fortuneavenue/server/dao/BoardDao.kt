package com.fortuneavenue.server.dao

import com.fortuneavenue.server.models.board.db.Board
import com.fortuneavenue.server.models.board.db.BoardGraph
import com.fortuneavenue.server.models.board.db.BoardPath
import com.fortuneavenue.server.models.board.db.BoardPathsTable
import com.fortuneavenue.server.models.board.db.BoardSpace
import com.fortuneavenue.server.models.board.db.BoardSpacesTable
import com.fortuneavenue.server.models.board.db.BoardsTable
import com.fortuneavenue.server.models.board.db.ShopInformation
import com.fortuneavenue.server.models.board.db.ShopInformationTable
import com.fortuneavenue.server.models.board.db.SpaceType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import kotlin.uuid.Uuid

@Repository
class BoardDao {

	data class SpaceInput(
		val spaceType: SpaceType,
		val baseValue: Int? = null,
		val basePricePercentage: BigDecimal? = null,
	)
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

		// Assumes the caller (BoardService, via ShopSpaceValidator) already checked
		// that every SHOP space input carries a baseValue/basePricePercentage.
		val shopInformation = spaceInputs.zip(spaces).mapNotNull { (input, space) ->
			if (input.spaceType != SpaceType.SHOP) return@mapNotNull null

			ShopInformation.new {
				boardId = board.id
				spaceId = space.id
				baseValue = requireNotNull(input.baseValue) {
					"SHOP space at ${space.id.value} is missing baseValue."
				}
				basePricePercentage = requireNotNull(input.basePricePercentage) {
					"SHOP space at ${space.id.value} is missing basePricePercentage."
				}
			}
		}

		BoardGraph(board = board, spaces = spaces, paths = paths, shopInformation = shopInformation)
	}

	fun findById(id: Uuid): BoardGraph? = transaction {
		val board = Board.findById(id) ?: return@transaction null
		val spaces = BoardSpace.find { BoardSpacesTable.boardId eq board.id }.toList()
		val paths = BoardPath.find { BoardPathsTable.boardId eq board.id }.toList()
		val shopInformation = ShopInformation.find { ShopInformationTable.boardId eq board.id }.toList()

		BoardGraph(board = board, spaces = spaces, paths = paths, shopInformation = shopInformation)
	}

	/** Boards are sorted by name until we add sort criteria. */
	fun findPage(page: Int, pageSize: Int, ascending: Boolean = true): List<BoardGraph> = transaction {
		val sortOrder = if (ascending) SortOrder.ASC else SortOrder.DESC

		val query = BoardsTable.selectAll()
			.orderBy(BoardsTable.name, sortOrder)
			.limit(pageSize)
			.offset(page.toLong() * pageSize)

		Board.wrapRows(query).map { board ->
			val spaces = BoardSpace.find { BoardSpacesTable.boardId eq board.id }.toList()
			val paths = BoardPath.find { BoardPathsTable.boardId eq board.id }.toList()
			val shopInformation = ShopInformation.find { ShopInformationTable.boardId eq board.id }.toList()
			BoardGraph(board = board, spaces = spaces, paths = paths, shopInformation = shopInformation)
		}
	}

	/**
	 * Total number of boards, regardless of any page/pageSize -- used to compute how many pages [findPage] has.
	 * Will eventually need to make this take search criteria, but we don't have any yet.
	 * */
	fun count(): Long = transaction { BoardsTable.selectAll().count() }
}
