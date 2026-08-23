package com.fortuneavenue.server.dao

import com.fortuneavenue.server.DatabaseTest
import com.fortuneavenue.server.models.board.db.SpaceType
import com.fortuneavenue.server.models.game.db.Game
import java.math.BigDecimal
import kotlin.uuid.Uuid
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class PlayerStockDaoTest : DatabaseTest() {

    @Autowired lateinit var playerStockDao: PlayerStockDao

    @Autowired lateinit var playerDao: PlayerDao

    @Autowired lateinit var gameDao: GameDao

    @Autowired lateinit var boardDao: BoardDao

    @Autowired lateinit var gameDistrictInformationDao: GameDistrictInformationDao

    @Autowired lateinit var gameShopInformationDao: GameShopInformationDao

    private fun createGame(): Game {
        val boardId =
            boardDao
                .create(
                    name = "board-${Uuid.random()}",
                    spaceInputs = listOf(BoardDao.SpaceInput(SpaceType.BASIC)),
                    pathInputs = emptyList(),
                    startIndex = 0,
                )
                .board
                .id
                .value
        return gameDao.create(boardId, 6000)
    }

    /**
     * A seeded game_district_information row -- player_stocks has a real FK to it, so tests need an
     * actual one, not just any UUID.
     */
    private fun createGameDistrictInformation(): Uuid {
        val board =
            boardDao.create(
                name = "board-${Uuid.random()}",
                spaceInputs =
                    listOf(
                        BoardDao.SpaceInput(
                            spaceType = SpaceType.SHOP,
                            baseValue = 100,
                            basePricePercentage = BigDecimal("0.1000"),
                            districtIndex = 0,
                        )
                    ),
                pathInputs = emptyList(),
                startIndex = 0,
                districtInputs =
                    listOf(
                        BoardDao.DistrictInput(
                            name = "district-${Uuid.random()}",
                            colorHex = "336699",
                            minimumStockPercentage = BigDecimal("0.5000"),
                        )
                    ),
            )
        val game = gameDao.create(board.board.id.value, 6000)
        val seededShops = gameShopInformationDao.seedForGame(game.id.value, board)
        val seededDistrictInfo =
            gameDistrictInformationDao.seedForGame(game.id.value, board, seededShops)
        return seededDistrictInfo.single().id.value
    }

    @Test
    fun `adjustQuantity creates a new row starting from the given delta, the first time a player trades a district's stock`() {
        val player = playerDao.create(gameId = createGame().id.value)
        val gameDistrictInformationId = createGameDistrictInformation()

        val stock = playerStockDao.adjustQuantity(player.id.value, gameDistrictInformationId, 10)

        assertThat(stock.playerId.value).isEqualTo(player.id.value)
        assertThat(stock.gameDistrictInformationId.value).isEqualTo(gameDistrictInformationId)
        assertThat(stock.quantity).isEqualTo(10)
    }

    @Test
    fun `adjustQuantity adds a positive or negative delta to an existing row's quantity`() {
        val player = playerDao.create(gameId = createGame().id.value)
        val gameDistrictInformationId = createGameDistrictInformation()
        playerStockDao.adjustQuantity(player.id.value, gameDistrictInformationId, 20)

        val afterSale =
            playerStockDao.adjustQuantity(player.id.value, gameDistrictInformationId, -5)

        assertThat(afterSale.quantity).isEqualTo(15)
        assertThat(playerStockDao.find(player.id.value, gameDistrictInformationId)!!.quantity)
            .isEqualTo(15)
    }

    @Test
    fun `find returns null when the player has never traded that district's stock`() {
        val player = playerDao.create(gameId = createGame().id.value)
        val gameDistrictInformationId = createGameDistrictInformation()

        val result = playerStockDao.find(player.id.value, gameDistrictInformationId)

        assertThat(result).isNull()
    }

    @Test
    fun `findByPlayer returns every district a player has traded stock in`() {
        val player = playerDao.create(gameId = createGame().id.value)
        val firstDistrictInfoId = createGameDistrictInformation()
        val secondDistrictInfoId = createGameDistrictInformation()
        playerStockDao.adjustQuantity(player.id.value, firstDistrictInfoId, 5)
        playerStockDao.adjustQuantity(player.id.value, secondDistrictInfoId, 8)

        val holdings = playerStockDao.findByPlayer(player.id.value)

        assertThat(holdings.map { it.gameDistrictInformationId.value })
            .containsExactlyInAnyOrder(firstDistrictInfoId, secondDistrictInfoId)
    }

    @Test
    fun `findByPlayer returns only holdings belonging to that player`() {
        val playerOne = playerDao.create(gameId = createGame().id.value)
        val playerTwo = playerDao.create(gameId = createGame().id.value)
        val gameDistrictInformationId = createGameDistrictInformation()
        playerStockDao.adjustQuantity(playerOne.id.value, gameDistrictInformationId, 5)

        val holdings = playerStockDao.findByPlayer(playerTwo.id.value)

        assertThat(holdings).isEmpty()
    }
}
