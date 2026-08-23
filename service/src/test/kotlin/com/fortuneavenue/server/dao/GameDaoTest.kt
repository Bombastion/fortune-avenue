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

        val created = gameDao.create(boardId, 6000)
        val found = gameDao.findById(created.id.value)

        assertThat(found).isNotNull()
        assertThat(found!!.id.value).isEqualTo(created.id.value)
        assertThat(found.boardId.value).isEqualTo(boardId)
    }

    @Test
    fun `create starts a game at turn zero, with no turn order yet, a default max turns, and no ended-on-turn recorded`() {
        val created = gameDao.create(createBoardId(), 6000)

        assertThat(created.turnNumber).isEqualTo(0)
        assertThat(created.turnOrder).isNull()
        assertThat(created.maxTurns).isEqualTo(10)
        assertThat(created.currentMovementPoints).isNull()
        assertThat(created.endedOnTurn).isNull()
    }

    @Test
    fun `create persists whatever targetNetWorth it's given`() {
        val created = gameDao.create(createBoardId(), 9000)

        assertThat(created.targetNetWorth).isEqualTo(9000)
        assertThat(gameDao.findById(created.id.value)!!.targetNetWorth).isEqualTo(9000)
    }

    @Test
    fun `setMovementPoints records remaining movement`() {
        val game = gameDao.create(createBoardId(), 6000)

        val updated = gameDao.setMovementPoints(game.id.value, 3)

        assertThat(updated).isNotNull()
        assertThat(updated!!.currentMovementPoints).isEqualTo(3)
        assertThat(gameDao.findById(game.id.value)!!.currentMovementPoints).isEqualTo(3)
    }

    @Test
    fun `setMovementPoints can clear remaining movement back to null`() {
        val game = gameDao.create(createBoardId(), 6000)
        gameDao.setMovementPoints(game.id.value, 3)

        val updated = gameDao.setMovementPoints(game.id.value, null)

        assertThat(updated!!.currentMovementPoints).isNull()
    }

    @Test
    fun `startGame sets the turn order`() {
        val game = gameDao.create(createBoardId(), 6000)
        val order = listOf(Uuid.random(), Uuid.random())

        val started = gameDao.startGame(game.id.value, order)

        assertThat(started).isNotNull()
        assertThat(started!!.turnOrder).isEqualTo(order)
        assertThat(gameDao.findById(game.id.value)!!.turnOrder).isEqualTo(order)
    }

    @Test
    fun `advanceTurn increments the turn number`() {
        val game = gameDao.create(createBoardId(), 6000)

        val advanced = gameDao.advanceTurn(game.id.value)

        assertThat(advanced).isNotNull()
        assertThat(advanced!!.turnNumber).isEqualTo(1)
        assertThat(gameDao.findById(game.id.value)!!.turnNumber).isEqualTo(1)
    }

    @Test
    fun `advanceTurn clears any leftover movement points`() {
        val game = gameDao.create(createBoardId(), 6000)
        gameDao.setMovementPoints(game.id.value, 2)

        val advanced = gameDao.advanceTurn(game.id.value)

        assertThat(advanced!!.currentMovementPoints).isNull()
        assertThat(gameDao.findById(game.id.value)!!.currentMovementPoints).isNull()
    }

    @Test
    fun `setEndedOnTurn records the turn number a game ended on`() {
        val game = gameDao.create(createBoardId(), 6000)

        val ended = gameDao.setEndedOnTurn(game.id.value, 4)

        assertThat(ended).isNotNull()
        assertThat(ended!!.endedOnTurn).isEqualTo(4)
        assertThat(gameDao.findById(game.id.value)!!.endedOnTurn).isEqualTo(4)
    }

    @Test
    fun `setEndedOnTurn is a plain write -- it doesn't touch turnNumber at all`() {
        val game = gameDao.create(createBoardId(), 6000)
        gameDao.advanceTurn(game.id.value)
        gameDao.advanceTurn(game.id.value)

        val ended = gameDao.setEndedOnTurn(game.id.value, 2)

        assertThat(ended!!.turnNumber).isEqualTo(2)
        assertThat(ended.endedOnTurn).isEqualTo(2)
    }

    @Test
    fun `findById returns null for an id that does not exist`() {
        val result = gameDao.findById(Uuid.random())

        assertThat(result).isNull()
    }
}
