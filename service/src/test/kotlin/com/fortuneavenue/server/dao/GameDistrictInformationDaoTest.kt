package com.fortuneavenue.server.dao

import com.fortuneavenue.server.DatabaseTest
import com.fortuneavenue.server.models.board.db.BoardGraph
import com.fortuneavenue.server.models.board.db.SpaceType
import java.math.BigDecimal
import kotlin.uuid.Uuid
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class GameDistrictInformationDaoTest : DatabaseTest() {

    @Autowired lateinit var gameDistrictInformationDao: GameDistrictInformationDao

    @Autowired lateinit var gameShopInformationDao: GameShopInformationDao

    @Autowired lateinit var boardDao: BoardDao

    @Autowired lateinit var gameDao: GameDao

    /**
     * A board with two districts -- "Red" has two SHOP spaces (base values 100 and 200) plus a
     * third SHOP space outside any district, "Blue" has only a BASIC space and no shops at all.
     */
    private fun createBoardWithShops(redStockPercentage: String = "0.5000"): BoardGraph {
        val districts =
            listOf(
                BoardDao.DistrictInput("Red", "FF0000", BigDecimal(redStockPercentage)),
                BoardDao.DistrictInput("Blue", "0000FF", BigDecimal("0.5000")),
            )
        val spaces =
            listOf(
                BoardDao.SpaceInput(
                    SpaceType.SHOP,
                    baseValue = 100,
                    basePricePercentage = BigDecimal("0.2500"),
                    districtIndex = 0,
                ),
                BoardDao.SpaceInput(
                    SpaceType.SHOP,
                    baseValue = 200,
                    basePricePercentage = BigDecimal("0.2500"),
                    districtIndex = 0,
                ),
                BoardDao.SpaceInput(
                    SpaceType.SHOP,
                    baseValue = 300,
                    basePricePercentage = BigDecimal("0.2500"),
                ),
                BoardDao.SpaceInput(SpaceType.BASIC, districtIndex = 1),
            )
        val paths =
            listOf(
                BoardDao.PathInput(0, 1, 0),
                BoardDao.PathInput(1, 2, 0),
                BoardDao.PathInput(2, 3, 0),
                BoardDao.PathInput(3, 0, 0),
            )
        return boardDao.create(
            name = "district-stock-board-${Uuid.random()}",
            spaceInputs = spaces,
            pathInputs = paths,
            startIndex = 0,
            districtInputs = districts,
        )
    }

    private fun createGameId(boardId: Uuid) = gameDao.create(boardId).id.value

    @Test
    fun `seedForGame persists one row per district with at least one SHOP space, skipping districts with none`() {
        val boardGraph = createBoardWithShops()
        val gameId = createGameId(boardGraph.board.id.value)
        val seededShops = gameShopInformationDao.seedForGame(gameId, boardGraph)

        val seeded = gameDistrictInformationDao.seedForGame(gameId, boardGraph, seededShops)

        // Only "Red" has any SHOP spaces -- "Blue" (BASIC-only) has nothing to average, and the
        // third SHOP space isn't in any district at all.
        assertThat(seeded).hasSize(1)
        val redInfo = seeded.single()
        val redDistrict = boardGraph.districts.single { it.name == "Red" }
        assertThat(redInfo.gameId.value).isEqualTo(gameId)
        assertThat(redInfo.districtId).isEqualTo(redDistrict.id)
        assertThat(redInfo.boardId).isEqualTo(boardGraph.board.id)
        assertThat(redInfo.minimumStockPercentage).isEqualByComparingTo(BigDecimal("0.5000"))
        // average(100, 200) = 150; 150 * 0.5 = 75.
        assertThat(redInfo.currentStockValue).isEqualTo(75)
    }

    @Test
    fun `seedForGame rounds current stock value to the nearest whole gold`() {
        val districts = listOf(BoardDao.DistrictInput("Red", "FF0000", BigDecimal("0.2500")))
        val spaces =
            listOf(
                BoardDao.SpaceInput(
                    SpaceType.SHOP,
                    baseValue = 1,
                    basePricePercentage = BigDecimal("0.2500"),
                    districtIndex = 0,
                ),
                BoardDao.SpaceInput(
                    SpaceType.SHOP,
                    baseValue = 3,
                    basePricePercentage = BigDecimal("0.2500"),
                    districtIndex = 0,
                ),
            )
        val paths = listOf(BoardDao.PathInput(0, 1, 0), BoardDao.PathInput(1, 0, 0))
        val boardGraph =
            boardDao.create(
                name = "rounding-board-${Uuid.random()}",
                spaceInputs = spaces,
                pathInputs = paths,
                startIndex = 0,
                districtInputs = districts,
            )
        val gameId = createGameId(boardGraph.board.id.value)
        val seededShops = gameShopInformationDao.seedForGame(gameId, boardGraph)

        val seeded = gameDistrictInformationDao.seedForGame(gameId, boardGraph, seededShops)

        // average(1, 3) = 2; 2 * 0.2500 = 0.5, which rounds up to 1 under HALF_UP.
        assertThat(seeded.single().currentStockValue).isEqualTo(1)
    }

    @Test
    fun `findByGameAndDistrict finds the seeded row for a district, and null for one with no shops`() {
        val boardGraph = createBoardWithShops()
        val gameId = createGameId(boardGraph.board.id.value)
        val seededShops = gameShopInformationDao.seedForGame(gameId, boardGraph)
        gameDistrictInformationDao.seedForGame(gameId, boardGraph, seededShops)

        val redDistrictId = boardGraph.districts.single { it.name == "Red" }.id.value
        val blueDistrictId = boardGraph.districts.single { it.name == "Blue" }.id.value

        assertThat(
                gameDistrictInformationDao
                    .findByGameAndDistrict(gameId, redDistrictId)
                    ?.currentStockValue
            )
            .isEqualTo(75)
        assertThat(gameDistrictInformationDao.findByGameAndDistrict(gameId, blueDistrictId))
            .isNull()
    }

    @Test
    fun `recalculateCurrentStockValue re-averages from the given shops and persists the result`() {
        val boardGraph = createBoardWithShops()
        val gameId = createGameId(boardGraph.board.id.value)
        val seededShops = gameShopInformationDao.seedForGame(gameId, boardGraph)
        gameDistrictInformationDao.seedForGame(gameId, boardGraph, seededShops)
        val redDistrict = boardGraph.districts.single { it.name == "Red" }

        // Simulate a district value progression having boosted one of the two shops (as
        // GameSimulationService.purchaseShop would) before re-reading the district's shops.
        val inDistrictShops = seededShops.filter { it.districtId == redDistrict.id }
        gameShopInformationDao.setCurrentValue(inDistrictShops[0].id.value, 150)
        val refreshedShops = gameShopInformationDao.findByGameAndDistrict(gameId, redDistrict.id)

        val updated =
            gameDistrictInformationDao.recalculateCurrentStockValue(
                gameId,
                redDistrict.id,
                refreshedShops,
            )

        // average(150, 200) = 175; 175 * 0.5 = 87.5, which rounds up to 88 under HALF_UP.
        assertThat(updated?.currentStockValue).isEqualTo(88)
        assertThat(
                gameDistrictInformationDao
                    .findByGameAndDistrict(gameId, redDistrict.id.value)
                    ?.currentStockValue
            )
            .isEqualTo(88)
    }

    @Test
    fun `recalculateCurrentStockValue returns null for a district with no seeded row`() {
        val boardGraph = createBoardWithShops()
        val gameId = createGameId(boardGraph.board.id.value)
        val seededShops = gameShopInformationDao.seedForGame(gameId, boardGraph)
        gameDistrictInformationDao.seedForGame(gameId, boardGraph, seededShops)
        val blueDistrict = boardGraph.districts.single { it.name == "Blue" }

        // "Blue" has no shops and so no seeded row -- pass a non-empty (if unrelated) shops list
        // to isolate the missing-row branch from the empty-shops guard.
        val result =
            gameDistrictInformationDao.recalculateCurrentStockValue(
                gameId,
                blueDistrict.id,
                seededShops,
            )

        assertThat(result).isNull()
    }

    @Test
    fun `recalculateCurrentStockValue returns null when given no shops`() {
        val boardGraph = createBoardWithShops()
        val gameId = createGameId(boardGraph.board.id.value)
        val seededShops = gameShopInformationDao.seedForGame(gameId, boardGraph)
        gameDistrictInformationDao.seedForGame(gameId, boardGraph, seededShops)
        val redDistrict = boardGraph.districts.single { it.name == "Red" }

        val result =
            gameDistrictInformationDao.recalculateCurrentStockValue(
                gameId,
                redDistrict.id,
                emptyList(),
            )

        assertThat(result).isNull()
        assertThat(
                gameDistrictInformationDao
                    .findByGameAndDistrict(gameId, redDistrict.id.value)
                    ?.currentStockValue
            )
            .isEqualTo(75)
    }

    @Test
    fun `adjustStockValueForTrade moves the price up for a buy, by pre-trade value divided by 16 rounded down plus 1`() {
        val boardGraph = createBoardWithShops()
        val gameId = createGameId(boardGraph.board.id.value)
        val seededShops = gameShopInformationDao.seedForGame(gameId, boardGraph)
        gameDistrictInformationDao.seedForGame(gameId, boardGraph, seededShops)
        val redDistrict = boardGraph.districts.single { it.name == "Red" }
        val shops = gameShopInformationDao.findByGameAndDistrict(gameId, redDistrict.id)

        // Seeded currentStockValue is 75 (see the seedForGame test above); 75 / 16 = 4 (rounded
        // down), + 1 = 5, so a buy should land on 80.
        val updated =
            gameDistrictInformationDao.adjustStockValueForTrade(
                gameId,
                redDistrict.id,
                shops,
                isBuy = true,
            )

        assertThat(updated?.currentStockValue).isEqualTo(80)
        assertThat(
                gameDistrictInformationDao
                    .findByGameAndDistrict(gameId, redDistrict.id.value)
                    ?.currentStockValue
            )
            .isEqualTo(80)
    }

    @Test
    fun `adjustStockValueForTrade moves the price down for a sell, when there's room above the minimum`() {
        val boardGraph = createBoardWithShops()
        val gameId = createGameId(boardGraph.board.id.value)
        val seededShops = gameShopInformationDao.seedForGame(gameId, boardGraph)
        gameDistrictInformationDao.seedForGame(gameId, boardGraph, seededShops)
        val redDistrict = boardGraph.districts.single { it.name == "Red" }
        val shops = gameShopInformationDao.findByGameAndDistrict(gameId, redDistrict.id)

        // Two buys first, to lift the price well above the minimum (still average(100, 200) * 0.5
        // = 75, since neither buy touches the shops themselves) before selling back down:
        // 75 -> (+5) -> 80 -> (+5) -> 86 -> (-6) -> 80. Each step's delta is that step's starting
        // value / 16 (rounded down) + 1.
        gameDistrictInformationDao.adjustStockValueForTrade(gameId, redDistrict.id, shops, true)
        gameDistrictInformationDao.adjustStockValueForTrade(gameId, redDistrict.id, shops, true)

        val updated =
            gameDistrictInformationDao.adjustStockValueForTrade(
                gameId,
                redDistrict.id,
                shops,
                isBuy = false,
            )

        assertThat(updated?.currentStockValue).isEqualTo(80)
    }

    @Test
    fun `adjustStockValueForTrade never sells the price below the district's current minimum`() {
        val boardGraph = createBoardWithShops()
        val gameId = createGameId(boardGraph.board.id.value)
        val seededShops = gameShopInformationDao.seedForGame(gameId, boardGraph)
        gameDistrictInformationDao.seedForGame(gameId, boardGraph, seededShops)
        val redDistrict = boardGraph.districts.single { it.name == "Red" }
        val shops = gameShopInformationDao.findByGameAndDistrict(gameId, redDistrict.id)

        // Seeded currentStockValue (75) already equals the minimum (average(100, 200) * 0.5) --
        // a sell would otherwise drop it to 75 - (75 / 16 + 1) = 70, below that floor.
        val updated =
            gameDistrictInformationDao.adjustStockValueForTrade(
                gameId,
                redDistrict.id,
                shops,
                isBuy = false,
            )

        assertThat(updated?.currentStockValue).isEqualTo(75)
    }

    @Test
    fun `adjustStockValueForTrade returns null for a district with no seeded row`() {
        val boardGraph = createBoardWithShops()
        val gameId = createGameId(boardGraph.board.id.value)
        val seededShops = gameShopInformationDao.seedForGame(gameId, boardGraph)
        gameDistrictInformationDao.seedForGame(gameId, boardGraph, seededShops)
        val blueDistrict = boardGraph.districts.single { it.name == "Blue" }

        val result =
            gameDistrictInformationDao.adjustStockValueForTrade(
                gameId,
                blueDistrict.id,
                emptyList(),
                isBuy = true,
            )

        assertThat(result).isNull()
    }
}
