package com.fortuneavenue.server.dao

import com.fortuneavenue.server.models.board.db.SpaceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.uuid.Uuid

@SpringBootTest
class GameDaoTest {

	@Autowired
	lateinit var gameDao: GameDao

	@Autowired
	lateinit var boardDao: BoardDao

	private fun createBoardId(): Uuid = boardDao.create(
		name = "board-${Uuid.random()}",
		spaceInputs = listOf(BoardDao.SpaceInput(SpaceType.BASIC)),
		pathInputs = emptyList(),
		startIndex = 0,
	).board.id.value

	@Test
	fun `create persists a game tied to a board, that can then be found by id`() {
		val boardId = createBoardId()

		val created = gameDao.create(boardId)
		val found = gameDao.findById(created.id.value)

		assertThat(found).isNotNull()
		assertThat(found!!.id.value).isEqualTo(created.id.value)
		assertThat(found.boardId.value).isEqualTo(boardId)
	}

	@Test
	fun `findById returns null for an id that does not exist`() {
		val result = gameDao.findById(Uuid.random())

		assertThat(result).isNull()
	}
}
