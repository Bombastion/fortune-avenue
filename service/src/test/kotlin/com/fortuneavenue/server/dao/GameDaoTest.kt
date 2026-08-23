package com.fortuneavenue.server.dao

import com.fortuneavenue.server.DatabaseTest
import com.fortuneavenue.server.models.board.db.SpaceType
import kotlin.uuid.Uuid
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class GameDaoTest : DatabaseTest() {

    @Autowired lateinit var gameDao: GameDao

    @Autowired lateinit var boardDao: BoardDao

    private fun createBoardId(): Uuid =
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
    fun `create starts a game at turn zero, with no turn order yet, and a default max turns`() {
        val created = gameDao.create(createBoardId())

        assertThat(created.turnNumber).isEqualTo(0)
        assertThat(created.turnOrder).isNull()
        assertThat(created.maxTurns).isEqualTo(10)
        assertThat(created.currentMovementPoints).isNull()
    }

    @Test
    fun `create defaults target net worth to 6000 gold`() {
        val created = gameDao.create(createBoardId())

        assertThat(created.targetNetWorth).isEqualTo(6000)
    }

    @Test
    fun `create can override target net worth`() {
        val created = gameDao.create(createBoardId(), targetNetWorth = 9000)

        assertThat(created.targetNetWorth).isEqualTo(9000)
        assertThat(gameDao.findById(created.id.value)!!.targetNetWorth).isEqualTo(9000)
    }

    @Test
    fun `setMovementPoints records remaining movement`() {
        val game = gameDao.create(createBoardId())

        val updated = gameDao.setMovementPoints(game.id.value, 3)

        assertThat(updated).isNotNull()
        assertThat(updated!!.currentMovementPoints).isEqualTo(3)
        assertThat(gameDao.findById(game.id.value)!!.currentMovementPoints).isEqualTo(3)
    }

    @Test
    fun `setMovementPoints can clear remaining movement back to null`() {
        val game = gameDao.create(createBoardId())
        gameDao.setMovementPoints(game.id.value, 3)

        val updated = gameDao.setMovementPoints(game.id.value, null)

        assertThat(updated!!.currentMovementPoints).isNull()
    }

    @Test
    fun `startGame sets the turn order`() {
        val game = gameDao.create(createBoardId())
        val order = listOf(Uuid.random(), Uuid.random())

        val started = gameDao.startGame(game.id.value, order)

        assertThat(started).isNotNull()
        assertThat(started!!.turnOrder).isEqualTo(order)
        assertThat(gameDao.findById(game.id.value)!!.turnOrder).isEqualTo(order)
    }

    @Test
    fun `advanceTurn increments the turn number`() {
        val game = gameDao.create(createBoardId())

        val advanced = gameDao.advanceTurn(game.id.value)

        assertThat(advanced).isNotNull()
        assertThat(advanced!!.turnNumber).isEqualTo(1)
        assertThat(gameDao.findById(game.id.value)!!.turnNumber).isEqualTo(1)
    }

    @Test
    fun `advanceTurn clears any leftover movement points`() {
        val game = gameDao.create(createBoardId())
        gameDao.setMovementPoints(game.id.value, 2)

        val advanced = gameDao.advanceTurn(game.id.value)

        assertThat(advanced!!.currentMovementPoints).isNull()
        assertThat(gameDao.findById(game.id.value)!!.currentMovementPoints).isNull()
    }

    @Test
    fun `endGameEarly fast-forwards the turn number to max turns`() {
        val game = gameDao.create(createBoardId())

        val ended = gameDao.endGameEarly(game.id.value)

        assertThat(ended).isNotNull()
        assertThat(ended!!.turnNumber).isEqualTo(ended.maxTurns)
        assertThat(gameDao.findById(game.id.value)!!.turnNumber).isEqualTo(ended.maxTurns)
    }

    @Test
    fun `endGameEarly never lowers the turn number if it's already past max turns`() {
        val game = gameDao.create(createBoardId())
        repeat(game.maxTurns + 2) { gameDao.advanceTurn(game.id.value) }
        val pastMaxTurns = gameDao.findById(game.id.value)!!.turnNumber

        val ended = gameDao.endGameEarly(game.id.value)

        assertThat(ended!!.turnNumber).isEqualTo(pastMaxTurns)
    }

    @Test
    fun `findById returns null for an id that does not exist`() {
        val result = gameDao.findById(Uuid.random())

        assertThat(result).isNull()
    }
}
