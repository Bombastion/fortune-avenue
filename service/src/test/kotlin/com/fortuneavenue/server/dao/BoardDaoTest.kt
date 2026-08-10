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

	/**
	 * This table is shared across every test in the suite and nothing ever
	 * cleans it up between runs, so these tests can't assume they're the
	 * only rows in it. Each one uses a name prefixed with a fresh random id
	 * (guaranteed not to collide with anything another test created) so its
	 * own boards can be picked back out of a page that may also contain
	 * plenty of unrelated ones.
	 */
	private fun createBoardWithName(name: String) = boardDao.create(
		name = name,
		spaceInputs = listOf(BoardDao.SpaceInput(SpaceType.BASIC)),
		pathInputs = emptyList(),
		startIndex = 0,
	)

	@Test
	fun `findPage sorts boards by name ascending by default`() {
		val prefix = "pg-${Uuid.random()}-"
		val names = listOf("${prefix}1", "${prefix}2", "${prefix}3")
		names.shuffled().forEach { createBoardWithName(it) }

		val ours = boardDao.findPage(page = 0, pageSize = LARGE_PAGE_SIZE, ascending = true)
			.filter { it.board.name.startsWith(prefix) }

		assertThat(ours.map { it.board.name }).containsExactly(names[0], names[1], names[2])
	}

	@Test
	fun `findPage sorts descending when ascending is false`() {
		val prefix = "pg-${Uuid.random()}-"
		val names = listOf("${prefix}1", "${prefix}2", "${prefix}3")
		names.shuffled().forEach { createBoardWithName(it) }

		val ours = boardDao.findPage(page = 0, pageSize = LARGE_PAGE_SIZE, ascending = false)
			.filter { it.board.name.startsWith(prefix) }

		assertThat(ours.map { it.board.name }).containsExactly(names[2], names[1], names[0])
	}

	@Test
	fun `findPage never returns more boards than pageSize`() {
		val prefix = "pg-${Uuid.random()}-"
		repeat(3) { createBoardWithName("$prefix$it") }

		val page = boardDao.findPage(page = 0, pageSize = 1, ascending = true)

		assertThat(page).hasSizeLessThanOrEqualTo(1)
	}

	@Test
	fun `findPage returns an empty list once past the last page`() {
		val page = boardDao.findPage(page = FAR_PAST_ANY_REASONABLE_TABLE_SIZE, pageSize = 10, ascending = true)

		assertThat(page).isEmpty()
	}

	@Test
	fun `count increases as boards are created`() {
		val before = boardDao.count()

		createBoardWithName("count-me-${Uuid.random()}")

		assertThat(boardDao.count()).isEqualTo(before + 1)
	}

	companion object {
		private const val LARGE_PAGE_SIZE = 10_000
		private const val FAR_PAST_ANY_REASONABLE_TABLE_SIZE = 1_000_000
	}
}
