package com.fortuneavenue.server.dao

import com.fortuneavenue.server.models.board.db.Board
import com.fortuneavenue.server.models.board.db.BoardGraph
import com.fortuneavenue.server.models.board.db.BoardPath
import com.fortuneavenue.server.models.board.db.BoardPathsTable
import com.fortuneavenue.server.models.board.db.BoardSpace
import com.fortuneavenue.server.models.board.db.BoardSpacesTable
import com.fortuneavenue.server.models.board.db.BoardsTable
import com.fortuneavenue.server.models.board.db.District
import com.fortuneavenue.server.models.board.db.DistrictValueProgression
import com.fortuneavenue.server.models.board.db.DistrictValueProgressionsTable
import com.fortuneavenue.server.models.board.db.DistrictsTable
import com.fortuneavenue.server.models.board.db.ShopInformation
import com.fortuneavenue.server.models.board.db.ShopInformationTable
import com.fortuneavenue.server.models.board.db.SpaceType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import kotlin.uuid.Uuid

// Real starting gold amounts are always caller-supplied (CreateBoardRequest requires it, and
// BoardService validates it's positive) -- this only exists so DAO-level tests that don't care
// about gold at all don't have to invent a number.
private const val DEFAULT_STARTING_GOLD = 1000

@Repository
class BoardDao {

	data class SpaceInput(
		val spaceType: SpaceType,
		val baseValue: Int? = null,
		val basePricePercentage: BigDecimal? = null,
		val districtIndex: Int? = null,
	)
	data class PathInput(val fromIndex: Int, val toIndex: Int, val branchOrder: Int)
	data class ProgressionInput(val ownedShopCount: Int, val existingShopBoostPercentage: BigDecimal, val newShopBoostPercentage: BigDecimal)
	data class DistrictInput(val name: String, val colorHex: String, val progressionInputs: List<ProgressionInput> = emptyList())

	fun create(
		name: String,
		spaceInputs: List<SpaceInput>,
		pathInputs: List<PathInput>,
		startIndex: Int,
		districtInputs: List<DistrictInput> = emptyList(),
		startingGold: Int = DEFAULT_STARTING_GOLD,
	): BoardGraph = transaction {
		// start_space_id is a plain uuid column, not a typed reference() (see the
		// comment on BoardsTable), but Board requires it to exist. We do some
		// wonky stuff with flushing to make sure the space exists, then update
		// the board.
		val board = Board.new {
			this.name = name
			// `this.` is required here too -- create()'s own `startingGold` parameter
			// would otherwise shadow the entity's `startingGold` property.
			this.startingGold = startingGold
		}
		board.flush()

		// Districts have to exist (and be flushed to the DB) before spaces can
		// reference them, same reasoning as the flush below for spaces/paths.
		val districts = districtInputs.map { input ->
			District.new {
				boardId = board.id
				// `this.` is required here: create()'s own `name` parameter would
				// otherwise shadow the entity's `name` property (see the similar
				// `this.name = name` above for Board.new).
				this.name = input.name
				colorHex = input.colorHex
			}
		}
		districts.firstOrNull()?.flush()

		val districtProgressions = districtInputs.zip(districts).flatMap { (input, district) ->
			input.progressionInputs.map { progressionInput ->
				DistrictValueProgression.new {
					districtId = district.id
					ownedShopCount = progressionInput.ownedShopCount
					existingShopBoostPercentage = progressionInput.existingShopBoostPercentage
					newShopBoostPercentage = progressionInput.newShopBoostPercentage
				}
			}
		}

		val spaces = spaceInputs.map { input ->
			BoardSpace.new {
				boardId = board.id
				spaceType = input.spaceType
				districtId = input.districtIndex?.let { districts[it].id }
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

		BoardGraph(
			board = board,
			spaces = spaces,
			paths = paths,
			shopInformation = shopInformation,
			districts = districts,
			districtProgressions = districtProgressions,
		)
	}

	private fun districtProgressionsFor(districts: List<District>): List<DistrictValueProgression> {
		if (districts.isEmpty()) return emptyList()
		val districtIds = districts.map { it.id }
		return DistrictValueProgression.find { DistrictValueProgressionsTable.districtId inList districtIds }.toList()
	}

	/** A single-column lookup for [com.fortuneavenue.server.service.PlayerService] -- cheaper than loading a full [BoardGraph] via [findById]. */
	fun findStartingGold(id: Uuid): Int? = transaction {
		Board.findById(id)?.startingGold
	}

	fun findById(id: Uuid): BoardGraph? = transaction {
		val board = Board.findById(id) ?: return@transaction null
		val spaces = BoardSpace.find { BoardSpacesTable.boardId eq board.id }.toList()
		val paths = BoardPath.find { BoardPathsTable.boardId eq board.id }.toList()
		val shopInformation = ShopInformation.find { ShopInformationTable.boardId eq board.id }.toList()
		val districts = District.find { DistrictsTable.boardId eq board.id }.toList()
		val districtProgressions = districtProgressionsFor(districts)

		BoardGraph(
			board = board,
			spaces = spaces,
			paths = paths,
			shopInformation = shopInformation,
			districts = districts,
			districtProgressions = districtProgressions,
		)
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
			val districts = District.find { DistrictsTable.boardId eq board.id }.toList()
			val districtProgressions = districtProgressionsFor(districts)
			BoardGraph(
				board = board,
				spaces = spaces,
				paths = paths,
				shopInformation = shopInformation,
				districts = districts,
				districtProgressions = districtProgressions,
			)
		}
	}

	/**
	 * Total number of boards, regardless of any page/pageSize -- used to compute how many pages [findPage] has.
	 * Will eventually need to make this take search criteria, but we don't have any yet.
	 * */
	fun count(): Long = transaction { BoardsTable.selectAll().count() }
}
