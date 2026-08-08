package com.fortuneavenue.server.dao

import com.fortuneavenue.server.models.board.db.SpaceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.uuid.Uuid

@SpringBootTest
class BoardDaoTest {

	@Autowired
	lateinit var boardDao: BoardDao

	@Test
	fun `create persists a board with its spaces and paths, and sets the start space`() {
		val spaces = listOf(
			BoardDao.SpaceInput(SpaceType.BASIC),
			BoardDao.SpaceInput(SpaceType.BASIC),
			BoardDao.SpaceInput(SpaceType.BASIC),
		)
		val paths = listOf(
			BoardDao.PathInput(0, 1, 0),
			BoardDao.PathInput(1, 2, 0),
			BoardDao.PathInput(2, 0, 0),
		)

		val created = boardDao.create(
			name = "loop-${Uuid.random()}",
			spaceInputs = spaces,
			pathInputs = paths,
			startIndex = 0,
		)

		assertThat(created.spaces).hasSize(3)
		assertThat(created.paths).hasSize(3)
		assertThat(created.board.startSpaceId).isEqualTo(created.spaces[0].id.value)

		val found = boardDao.findById(created.board.id.value)

		assertThat(found).isNotNull()
		assertThat(found!!.board.id.value).isEqualTo(created.board.id.value)
		assertThat(found.spaces.map { it.id.value })
			.containsExactlyInAnyOrderElementsOf(created.spaces.map { it.id.value })
		assertThat(found.paths.map { it.id.value })
			.containsExactlyInAnyOrderElementsOf(created.paths.map { it.id.value })
	}

	@Test
	fun `findById returns null for an id that does not exist`() {
		val result = boardDao.findById(Uuid.random())

		assertThat(result).isNull()
	}
}
