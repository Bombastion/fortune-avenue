package com.fortuneavenue.server.service

import com.fortuneavenue.server.dao.BoardDao
import com.fortuneavenue.server.models.board.db.BoardGraph
import com.fortuneavenue.server.models.board.db.SpaceType
import com.fortuneavenue.server.models.board.rest.CreateBoardPathRequest
import com.fortuneavenue.server.models.board.rest.CreateBoardRequest
import com.fortuneavenue.server.models.board.rest.CreateBoardSpaceRequest
import com.fortuneavenue.server.models.board.rest.CreateDistrictProgressionRequest
import com.fortuneavenue.server.models.board.rest.CreateDistrictRequest
import com.fortuneavenue.server.models.common.rest.SortDirection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal

@ExtendWith(MockitoExtension::class)
class BoardServiceTest {

	@Mock
	lateinit var boardDao: BoardDao

	private lateinit var boardService: BoardService

	@BeforeEach
	fun setUp() {
		boardService = BoardService(boardDao)
	}

	private fun validRequest() = CreateBoardRequest(
		name = "Loop",
		spaces = listOf(
			CreateBoardSpaceRequest(SpaceType.BASIC),
			CreateBoardSpaceRequest(SpaceType.BASIC),
			CreateBoardSpaceRequest(SpaceType.BASIC),
		),
		paths = listOf(
			CreateBoardPathRequest(0, 1),
			CreateBoardPathRequest(1, 2),
			CreateBoardPathRequest(2, 0),
		),
		startSpaceIndex = 0,
	)

	@Test
	fun `a valid board is validated then persisted via the DAO`() {
		val request = validRequest()
		val expectedGraph = mock(BoardGraph::class.java)

		val expectedSpaceInputs = request.spaces.map { BoardDao.SpaceInput(it.spaceType) }
		val expectedPathInputs = request.paths.map { BoardDao.PathInput(it.from, it.to, it.branchOrder) }

		given(
			boardDao.create(request.name, expectedSpaceInputs, expectedPathInputs, request.startSpaceIndex),
		).willReturn(expectedGraph)

		val result = boardService.createBoard(request)

		assertThat(result.isSuccess).isTrue()
		assertThat(result.getOrNull()).isSameAs(expectedGraph)
	}

	@Test
	fun `an invalid board is rejected without ever touching the DAO`() {
		// space index 2 is declared but no path makes it reachable from start
		val request = validRequest().copy(paths = listOf(CreateBoardPathRequest(0, 1)))

		val result = boardService.createBoard(request)

		assertThat(result.isFailure).isTrue()
		assertThat(result.exceptionOrNull()).isInstanceOf(InvalidBoardException::class.java)
		verifyNoInteractions(boardDao)
	}

	@Test
	fun `a board with a valid SHOP space is validated then persisted via the DAO`() {
		val request = validRequest().let { req ->
			req.copy(
				spaces = req.spaces.mapIndexed { index, space ->
					if (index == 1) CreateBoardSpaceRequest(SpaceType.SHOP, baseValue = 100, basePricePercentage = BigDecimal("0.1000")) else space
				},
			)
		}
		val expectedGraph = mock(BoardGraph::class.java)

		val expectedSpaceInputs = request.spaces.map {
			BoardDao.SpaceInput(spaceType = it.spaceType, baseValue = it.baseValue, basePricePercentage = it.basePricePercentage)
		}
		val expectedPathInputs = request.paths.map { BoardDao.PathInput(it.from, it.to, it.branchOrder) }

		given(
			boardDao.create(request.name, expectedSpaceInputs, expectedPathInputs, request.startSpaceIndex),
		).willReturn(expectedGraph)

		val result = boardService.createBoard(request)

		assertThat(result.isSuccess).isTrue()
		assertThat(result.getOrNull()).isSameAs(expectedGraph)
	}

	@Test
	fun `a SHOP space missing required fields is rejected without ever touching the DAO`() {
		val request = validRequest().let { req ->
			req.copy(spaces = req.spaces.mapIndexed { index, space -> if (index == 1) CreateBoardSpaceRequest(SpaceType.SHOP) else space })
		}

		val result = boardService.createBoard(request)

		assertThat(result.isFailure).isTrue()
		assertThat(result.exceptionOrNull()).isInstanceOf(InvalidBoardException::class.java)
		verifyNoInteractions(boardDao)
	}

	@Test
	fun `a non-SHOP space carrying shop fields is rejected without ever touching the DAO`() {
		val request = validRequest().let { req ->
			req.copy(
				spaces = req.spaces.mapIndexed { index, space -> if (index == 0) space.copy(baseValue = 100) else space },
			)
		}

		val result = boardService.createBoard(request)

		assertThat(result.isFailure).isTrue()
		assertThat(result.exceptionOrNull()).isInstanceOf(InvalidBoardException::class.java)
		verifyNoInteractions(boardDao)
	}

	// --- districts ---

	@Test
	fun `a board with a valid district is validated then persisted via the DAO`() {
		val request = validRequest().let { req ->
			req.copy(
				districts = listOf(CreateDistrictRequest("Red", "FF0000")),
				spaces = req.spaces.mapIndexed { index, space -> if (index == 0) space.copy(districtIndex = 0) else space },
			)
		}
		val expectedGraph = mock(BoardGraph::class.java)

		val expectedSpaceInputs = request.spaces.map {
			BoardDao.SpaceInput(
				spaceType = it.spaceType,
				baseValue = it.baseValue,
				basePricePercentage = it.basePricePercentage,
				districtIndex = it.districtIndex,
			)
		}
		val expectedPathInputs = request.paths.map { BoardDao.PathInput(it.from, it.to, it.branchOrder) }
		val expectedDistrictInputs = request.districts.map { BoardDao.DistrictInput(it.name, it.colorHex) }

		given(
			boardDao.create(request.name, expectedSpaceInputs, expectedPathInputs, request.startSpaceIndex, expectedDistrictInputs),
		).willReturn(expectedGraph)

		val result = boardService.createBoard(request)

		assertThat(result.isSuccess).isTrue()
		assertThat(result.getOrNull()).isSameAs(expectedGraph)
	}

	@Test
	fun `a district with a malformed colorHex is rejected without ever touching the DAO`() {
		val request = validRequest().copy(districts = listOf(CreateDistrictRequest("Red", "nope")))

		val result = boardService.createBoard(request)

		assertThat(result.isFailure).isTrue()
		assertThat(result.exceptionOrNull()).isInstanceOf(InvalidBoardException::class.java)
		verifyNoInteractions(boardDao)
	}

	@Test
	fun `a space with an out-of-range districtIndex is rejected without ever touching the DAO`() {
		val request = validRequest().let { req ->
			req.copy(spaces = req.spaces.mapIndexed { index, space -> if (index == 0) space.copy(districtIndex = 0) else space })
		}

		val result = boardService.createBoard(request)

		assertThat(result.isFailure).isTrue()
		assertThat(result.exceptionOrNull()).isInstanceOf(InvalidBoardException::class.java)
		verifyNoInteractions(boardDao)
	}

	@Test
	fun `a board with complete district progressions is validated then persisted via the DAO`() {
		val progression = CreateDistrictProgressionRequest(2, BigDecimal("0.1000"), BigDecimal("0.1500"))
		val request = validRequest().let { req ->
			req.copy(
				districts = listOf(CreateDistrictRequest("Red", "FF0000", progressions = listOf(progression))),
				spaces = req.spaces.mapIndexed { index, space -> if (index <= 1) space.copy(districtIndex = 0) else space },
			)
		}
		val expectedGraph = mock(BoardGraph::class.java)

		val expectedSpaceInputs = request.spaces.map {
			BoardDao.SpaceInput(
				spaceType = it.spaceType,
				baseValue = it.baseValue,
				basePricePercentage = it.basePricePercentage,
				districtIndex = it.districtIndex,
			)
		}
		val expectedPathInputs = request.paths.map { BoardDao.PathInput(it.from, it.to, it.branchOrder) }
		val expectedDistrictInputs = listOf(
			BoardDao.DistrictInput(
				name = "Red",
				colorHex = "FF0000",
				progressionInputs = listOf(BoardDao.ProgressionInput(2, BigDecimal("0.1000"), BigDecimal("0.1500"))),
			),
		)

		given(
			boardDao.create(request.name, expectedSpaceInputs, expectedPathInputs, request.startSpaceIndex, expectedDistrictInputs),
		).willReturn(expectedGraph)

		val result = boardService.createBoard(request)

		assertThat(result.isSuccess).isTrue()
		assertThat(result.getOrNull()).isSameAs(expectedGraph)
	}

	@Test
	fun `a district missing required progression levels is rejected without ever touching the DAO`() {
		val request = validRequest().let { req ->
			req.copy(
				districts = listOf(CreateDistrictRequest("Red", "FF0000")),
				spaces = req.spaces.mapIndexed { index, space -> if (index <= 1) space.copy(districtIndex = 0) else space },
			)
		}

		val result = boardService.createBoard(request)

		assertThat(result.isFailure).isTrue()
		assertThat(result.exceptionOrNull()).isInstanceOf(InvalidBoardException::class.java)
		verifyNoInteractions(boardDao)
	}

	// --- listBoards ---

	@Test
	fun `listBoards fails when page is negative`() {
		val result = boardService.listBoards(page = -1, pageSize = 10)

		assertThat(result.exceptionOrNull()).isInstanceOf(InvalidBoardException::class.java)
		verifyNoInteractions(boardDao)
	}

	@Test
	fun `listBoards fails when pageSize is less than 1`() {
		val result = boardService.listBoards(page = 0, pageSize = 0)

		assertThat(result.exceptionOrNull()).isInstanceOf(InvalidBoardException::class.java)
		verifyNoInteractions(boardDao)
	}

	@Test
	fun `listBoards defaults to ascending order and returns the requested page's metadata`() {
		val graphs = listOf(mock(BoardGraph::class.java), mock(BoardGraph::class.java))
		given(boardDao.findPage(page = 0, pageSize = 2, ascending = true)).willReturn(graphs)
		given(boardDao.count()).willReturn(5L)

		val result = boardService.listBoards(page = 0, pageSize = 2)

		val page = result.getOrNull()
		assertThat(page).isNotNull()
		assertThat(page!!.items).isEqualTo(graphs)
		assertThat(page.page).isEqualTo(0)
		assertThat(page.pageSize).isEqualTo(2)
		assertThat(page.direction).isEqualTo(SortDirection.ASC)
		// 5 boards at 2 per page is 3 pages (2 full pages + a partial third).
		assertThat(page.totalPages).isEqualTo(3)
	}

	@Test
	fun `listBoards passes descending order through to the DAO`() {
		given(boardDao.findPage(page = 1, pageSize = 3, ascending = false)).willReturn(emptyList())
		given(boardDao.count()).willReturn(0L)

		val result = boardService.listBoards(page = 1, pageSize = 3, direction = SortDirection.DESC)

		assertThat(result.isSuccess).isTrue()
		assertThat(result.getOrNull()?.direction).isEqualTo(SortDirection.DESC)
		assertThat(result.getOrNull()?.totalPages).isEqualTo(0)
		verify(boardDao).findPage(page = 1, pageSize = 3, ascending = false)
	}

	@Test
	fun `listBoards reports exactly one page when everything fits within pageSize`() {
		given(boardDao.findPage(page = 0, pageSize = 50, ascending = true)).willReturn(emptyList())
		given(boardDao.count()).willReturn(3L)

		val result = boardService.listBoards(page = 0, pageSize = 50)

		assertThat(result.getOrNull()?.totalPages).isEqualTo(1)
	}
}
