package com.fortuneavenue.server.service

import com.fortuneavenue.server.dao.BoardDao
import com.fortuneavenue.server.models.board.db.BoardGraph
import com.fortuneavenue.server.models.board.db.SpaceType
import com.fortuneavenue.server.models.board.rest.CreateBoardPathRequest
import com.fortuneavenue.server.models.board.rest.CreateBoardRequest
import com.fortuneavenue.server.models.board.rest.CreateBoardSpaceRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension

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
}
