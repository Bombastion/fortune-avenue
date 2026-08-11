package com.fortuneavenue.server.dao

import com.fortuneavenue.server.DatabaseTest
import com.fortuneavenue.server.models.board.db.SpaceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import kotlin.uuid.Uuid

@SpringBootTest
class BoardDaoTest : DatabaseTest() {

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
	fun `create persists shop information for SHOP spaces only, and findById returns it`() {
		val spaces = listOf(
			BoardDao.SpaceInput(SpaceType.BASIC),
			BoardDao.SpaceInput(SpaceType.SHOP, baseValue = 500, basePricePercentage = BigDecimal("0.2500")),
		)
		val paths = listOf(
			BoardDao.PathInput(0, 1, 0),
			BoardDao.PathInput(1, 0, 0),
		)

		val created = boardDao.create(
			name = "shop-board-${Uuid.random()}",
			spaceInputs = spaces,
			pathInputs = paths,
			startIndex = 0,
		)

		assertThat(created.shopInformation).hasSize(1)
		val shopSpaceId = created.spaces[1].id
		val createdShopInfo = created.shopInformation.single()
		assertThat(createdShopInfo.spaceId).isEqualTo(shopSpaceId)
		assertThat(createdShopInfo.baseValue).isEqualTo(500)
		assertThat(createdShopInfo.basePricePercentage).isEqualByComparingTo(BigDecimal("0.2500"))

		val found = boardDao.findById(created.board.id.value)

		assertThat(found).isNotNull()
		assertThat(found!!.shopInformation).hasSize(1)
		assertThat(found.shopInformation.single().spaceId).isEqualTo(shopSpaceId)
	}

	@Test
	fun `findById returns null for an id that does not exist`() {
		val result = boardDao.findById(Uuid.random())

		assertThat(result).isNull()
	}

	private fun createBoardWithName(name: String) = boardDao.create(
		name = name,
		spaceInputs = listOf(BoardDao.SpaceInput(SpaceType.BASIC)),
		pathInputs = emptyList(),
		startIndex = 0,
	)

	@Test
	fun `findPage sorts boards by name ascending by default`() {
		listOf("c", "a", "b").forEach { createBoardWithName(it) }

		val page = boardDao.findPage(page = 0, pageSize = 10, ascending = true)

		assertThat(page.map { it.board.name }).containsExactly("a", "b", "c")
	}

	@Test
	fun `findPage sorts descending when ascending is false`() {
		listOf("c", "a", "b").forEach { createBoardWithName(it) }

		val page = boardDao.findPage(page = 0, pageSize = 10, ascending = false)

		assertThat(page.map { it.board.name }).containsExactly("c", "b", "a")
	}

	@Test
	fun `findPage never returns more boards than pageSize`() {
		repeat(3) { createBoardWithName("board-$it") }

		val page = boardDao.findPage(page = 0, pageSize = 1, ascending = true)

		assertThat(page).hasSize(1)
	}

	@Test
	fun `findPage slices boards across pages without overlap`() {
		listOf("a", "b", "c").forEach { createBoardWithName(it) }

		val firstPage = boardDao.findPage(page = 0, pageSize = 2, ascending = true)
		val secondPage = boardDao.findPage(page = 1, pageSize = 2, ascending = true)

		assertThat(firstPage.map { it.board.name }).containsExactly("a", "b")
		assertThat(secondPage.map { it.board.name }).containsExactly("c")
	}

	@Test
	fun `findPage returns an empty list once past the last page`() {
		createBoardWithName("only-board-${Uuid.random()}")

		val page = boardDao.findPage(page = 1, pageSize = 10, ascending = true)

		assertThat(page).isEmpty()
	}

	@Test
	fun `count reflects exactly how many boards exist`() {
		assertThat(boardDao.count()).isZero()

		repeat(3) { createBoardWithName("board-$it") }

		assertThat(boardDao.count()).isEqualTo(3)
	}
}
