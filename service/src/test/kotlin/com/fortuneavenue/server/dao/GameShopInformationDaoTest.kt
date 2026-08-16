package com.fortuneavenue.server.dao

import com.fortuneavenue.server.DatabaseTest
import com.fortuneavenue.server.models.board.db.BoardGraph
import com.fortuneavenue.server.models.board.db.SpaceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import kotlin.uuid.Uuid

@SpringBootTest
class GameShopInformationDaoTest : DatabaseTest() {

	@Autowired
	lateinit var gameShopInformationDao: GameShopInformationDao

	@Autowired
	lateinit var boardDao: BoardDao

	@Autowired
	lateinit var gameDao: GameDao

	@Autowired
	lateinit var playerDao: PlayerDao

	/** A board with a district containing 2 SHOP spaces, plus a third SHOP space outside any district. */
	private fun createBoardWithShops(): BoardGraph {
		val districts = listOf(BoardDao.DistrictInput("Red", "FF0000", BigDecimal("0.5000")))
		val spaces = listOf(
			BoardDao.SpaceInput(SpaceType.SHOP, baseValue = 100, basePricePercentage = BigDecimal("0.2500"), districtIndex = 0),
			BoardDao.SpaceInput(SpaceType.SHOP, baseValue = 200, basePricePercentage = BigDecimal("0.2500"), districtIndex = 0),
			BoardDao.SpaceInput(SpaceType.SHOP, baseValue = 300, basePricePercentage = BigDecimal("0.2500")),
			BoardDao.SpaceInput(SpaceType.BASIC),
		)
		val paths = listOf(
			BoardDao.PathInput(0, 1, 0),
			BoardDao.PathInput(1, 2, 0),
			BoardDao.PathInput(2, 3, 0),
			BoardDao.PathInput(3, 0, 0),
		)
		return boardDao.create(
			name = "shop-board-${Uuid.random()}",
			spaceInputs = spaces,
			pathInputs = paths,
			startIndex = 0,
			districtInputs = districts,
		)
	}

	private fun createGameId(boardId: Uuid) = gameDao.create(boardId).id.value

	@Test
	fun `seedForGame persists one row per SHOP space, copying board fields and defaulting the rest`() {
		val boardGraph = createBoardWithShops()
		val gameId = createGameId(boardGraph.board.id.value)

		val seeded = gameShopInformationDao.seedForGame(gameId, boardGraph)

		assertThat(seeded).hasSize(3)
		val inDistrict = seeded.first { it.baseValue == 100 }
		assertThat(inDistrict.gameId.value).isEqualTo(gameId)
		assertThat(inDistrict.basePricePercentage).isEqualByComparingTo(BigDecimal("0.2500"))
		assertThat(inDistrict.currentValue).isEqualTo(100)
		assertThat(inDistrict.currentInvestment).isZero()
		assertThat(inDistrict.maxCap).isEqualTo(100)
		assertThat(inDistrict.ownerId).isNull()
		assertThat(inDistrict.districtId).isEqualTo(boardGraph.districts.single().id)

		val outsideDistrict = seeded.first { it.baseValue == 300 }
		assertThat(outsideDistrict.districtId).isNull()
	}

	@Test
	fun `findByGameAndSpace finds the seeded row for a space, and null for a space with no shop`() {
		val boardGraph = createBoardWithShops()
		val gameId = createGameId(boardGraph.board.id.value)
		gameShopInformationDao.seedForGame(gameId, boardGraph)

		val shopSpaceId = boardGraph.shopInformation.first { it.baseValue == 200 }.spaceId.value
		val basicSpaceId = boardGraph.spaces.first { it.spaceType == SpaceType.BASIC }.id.value

		assertThat(gameShopInformationDao.findByGameAndSpace(gameId, shopSpaceId)?.baseValue).isEqualTo(200)
		assertThat(gameShopInformationDao.findByGameAndSpace(gameId, basicSpaceId)).isNull()
	}

	@Test
	fun `setOwner sets ownerId, and setCurrentValue updates currentValue`() {
		val boardGraph = createBoardWithShops()
		val gameId = createGameId(boardGraph.board.id.value)
		val seeded = gameShopInformationDao.seedForGame(gameId, boardGraph)
		val player = playerDao.create(gameId)
		val shop = seeded.first()

		gameShopInformationDao.setOwner(shop.id.value, player.id.value)
		gameShopInformationDao.setCurrentValue(shop.id.value, 999)

		val updated = gameShopInformationDao.findByGameAndSpace(gameId, shop.spaceId.value)
		assertThat(updated?.ownerId?.value).isEqualTo(player.id.value)
		assertThat(updated?.currentValue).isEqualTo(999)
	}

	@Test
	fun `findOwnedByPlayerInDistrict returns only the given player's shops in that district`() {
		val boardGraph = createBoardWithShops()
		val gameId = createGameId(boardGraph.board.id.value)
		val seeded = gameShopInformationDao.seedForGame(gameId, boardGraph)
		val player = playerDao.create(gameId)
		val otherPlayer = playerDao.create(gameId)
		val districtId = boardGraph.districts.single().id

		val inDistrictShops = seeded.filter { it.districtId == districtId }
		gameShopInformationDao.setOwner(inDistrictShops[0].id.value, player.id.value)
		gameShopInformationDao.setOwner(inDistrictShops[1].id.value, otherPlayer.id.value)
		val outsideDistrictShop = seeded.first { it.districtId == null }
		gameShopInformationDao.setOwner(outsideDistrictShop.id.value, player.id.value)

		val owned = gameShopInformationDao.findOwnedByPlayerInDistrict(gameId, player.id.value, districtId)

		assertThat(owned).hasSize(1)
		assertThat(owned.single().id.value).isEqualTo(inDistrictShops[0].id.value)
	}

	@Test
	fun `findByGameAndDistrict returns every shop in that district regardless of owner`() {
		val boardGraph = createBoardWithShops()
		val gameId = createGameId(boardGraph.board.id.value)
		val seeded = gameShopInformationDao.seedForGame(gameId, boardGraph)
		val player = playerDao.create(gameId)
		val districtId = boardGraph.districts.single().id

		val inDistrictShops = seeded.filter { it.districtId == districtId }
		// One shop owned, one left unowned -- both should still come back.
		gameShopInformationDao.setOwner(inDistrictShops[0].id.value, player.id.value)

		val found = gameShopInformationDao.findByGameAndDistrict(gameId, districtId)

		assertThat(found.map { it.id.value }).containsExactlyInAnyOrderElementsOf(inDistrictShops.map { it.id.value })
	}
}
