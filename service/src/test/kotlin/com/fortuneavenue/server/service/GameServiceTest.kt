package com.fortuneavenue.server.service

import com.fortuneavenue.server.dao.GameDao
import com.fortuneavenue.server.models.game.db.Game
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import kotlin.uuid.Uuid

@ExtendWith(MockitoExtension::class)
class GameServiceTest {

	@Mock
	lateinit var gameDao: GameDao

	private lateinit var gameService: GameService

	@BeforeEach
	fun setUp() {
		gameService = GameService(gameDao)
	}

	@Test
	fun `createGame delegates to the DAO and returns its result`() {
		val createdGame = mock(Game::class.java)
		given(gameDao.create()).willReturn(createdGame)

		val result = gameService.createGame()

		assertThat(result).isSameAs(createdGame)
		verify(gameDao).create()
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
