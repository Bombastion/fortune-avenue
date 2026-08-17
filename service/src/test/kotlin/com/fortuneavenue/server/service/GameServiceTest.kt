package com.fortuneavenue.server.service

import com.fortuneavenue.server.dao.BoardDao
import com.fortuneavenue.server.dao.GameDao
import com.fortuneavenue.server.models.board.db.BoardGraph
import com.fortuneavenue.server.models.game.db.Game
import kotlin.uuid.Uuid
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class GameServiceTest {

    @Mock lateinit var gameDao: GameDao

    @Mock lateinit var boardDao: BoardDao

    private lateinit var gameService: GameService

    @BeforeEach
    fun setUp() {
        gameService = GameService(gameDao, boardDao)
    }

    @Test
    fun `createGame persists a game when the board exists`() {
        val boardId = Uuid.random()
        given(boardDao.findById(boardId)).willReturn(mock(BoardGraph::class.java))
        val createdGame = mock(Game::class.java)
        given(gameDao.create(boardId)).willReturn(createdGame)

        val result = gameService.createGame(boardId)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isSameAs(createdGame)
    }

    @Test
    fun `createGame rejects a boardId that doesn't belong to a real board`() {
        val boardId = Uuid.random()
        given(boardDao.findById(boardId)).willReturn(null)

        val result = gameService.createGame(boardId)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(InvalidGameException::class.java)
        verifyNoInteractions(gameDao)
    }

    @Test
    fun `getGame delegates to the DAO and returns its result when found`() {
        val id = Uuid.random()
        val foundGame = mock(Game::class.java)
        given(gameDao.findById(id)).willReturn(foundGame)

        val result = gameService.getGame(id)

        assertThat(result).isSameAs(foundGame)
        verify(gameDao).findById(id)
    }

    @Test
    fun `getGame returns null when the DAO finds nothing`() {
        val id = Uuid.random()
        given(gameDao.findById(id)).willReturn(null)

        val result = gameService.getGame(id)

        assertThat(result).isNull()
    }
}
