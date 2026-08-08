package com.fortuneavenue.server.service

import com.fortuneavenue.server.dao.GameDao
import com.fortuneavenue.server.dao.PlayerDao
import com.fortuneavenue.server.dao.UserDao
import com.fortuneavenue.server.models.game.db.Game
import com.fortuneavenue.server.models.player.db.Player
import com.fortuneavenue.server.models.user.db.User
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import kotlin.uuid.Uuid

@ExtendWith(MockitoExtension::class)
class PlayerServiceTest {

	@Mock
	lateinit var playerDao: PlayerDao

	@Mock
	lateinit var gameDao: GameDao

	@Mock
	lateinit var userDao: UserDao

	private lateinit var playerService: PlayerService

	private val gameId = Uuid.random()

	// Just needs *a* UuidTable to build EntityIDs against for stubbing --
	// which table is irrelevant since these ids are never resolved against
	// a real database in this test.
	private object AnyTable : UuidTable("any")

	private fun playerWithUser(userId: Uuid): Player {
		val player = mock(Player::class.java)
		given(player.userId).willReturn(EntityID(userId, AnyTable))
		return player
	}

	/** Stubs gameDao so [gameId] looks like a real, existing game. */
	private fun stubExistingGame() {
		given(gameDao.findById(gameId)).willReturn(mock(Game::class.java))
	}

	@BeforeEach
	fun setUp() {
		playerService = PlayerService(playerDao, gameDao, userDao)
	}

	@Test
	fun `addPlayer with no userId persists a player with no user, without consulting UserDao`() {
		stubExistingGame()
		val createdPlayer = mock(Player::class.java)
		given(playerDao.create(gameId, null)).willReturn(createdPlayer)

		val result = playerService.addPlayer(gameId, null)

		assertThat(result.isSuccess).isTrue()
		assertThat(result.getOrNull()).isSameAs(createdPlayer)
		verifyNoInteractions(userDao)
	}

	@Test
	fun `addPlayer with a real, not-yet-seated userId persists the player`() {
		stubExistingGame()
		val userId = Uuid.random()
		given(userDao.findById(userId)).willReturn(mock(User::class.java))
		given(playerDao.findByGameId(gameId)).willReturn(emptyList())
		val createdPlayer = mock(Player::class.java)
		given(playerDao.create(gameId, userId)).willReturn(createdPlayer)

		val result = playerService.addPlayer(gameId, userId)

		assertThat(result.isSuccess).isTrue()
		assertThat(result.getOrNull()).isSameAs(createdPlayer)
	}

	@Test
	fun `addPlayer rejects a userId that doesn't belong to a real user`() {
		stubExistingGame()
		val userId = Uuid.random()
		given(userDao.findById(userId)).willReturn(null)

		val result = playerService.addPlayer(gameId, userId)

		assertThat(result.isFailure).isTrue()
		assertThat(result.exceptionOrNull()).isInstanceOf(InvalidPlayerException::class.java)
	}

	@Test
	fun `addPlayer rejects a user who is already seated in the game`() {
		stubExistingGame()
		val userId = Uuid.random()
		given(userDao.findById(userId)).willReturn(mock(User::class.java))
		// Built and stubbed as its own statement, before the given()/willReturn()
		// below: nesting playerWithUser()'s given(...).willReturn(...) inside
		// this one's argument list would start a second stub while the first
		// is still open, waiting for its willReturn() -- Mockito rejects that
		// with an UnfinishedStubbingException.
		val seatedPlayer = playerWithUser(userId)
		given(playerDao.findByGameId(gameId)).willReturn(listOf(seatedPlayer))

		val result = playerService.addPlayer(gameId, userId)

		assertThat(result.isFailure).isTrue()
		assertThat(result.exceptionOrNull()).isInstanceOf(InvalidPlayerException::class.java)
	}

	@Test
	fun `addPlayer rejects a gameId that doesn't belong to a real game, without consulting UserDao or PlayerDao`() {
		given(gameDao.findById(gameId)).willReturn(null)

		val result = playerService.addPlayer(gameId, Uuid.random())

		assertThat(result.isFailure).isTrue()
		assertThat(result.exceptionOrNull()).isInstanceOf(GameNotFoundException::class.java)
		verifyNoInteractions(userDao)
		verifyNoInteractions(playerDao)
	}

	@Test
	fun `getPlayers delegates to the DAO when the game exists`() {
		stubExistingGame()
		val players = listOf(mock(Player::class.java), mock(Player::class.java))
		given(playerDao.findByGameId(gameId)).willReturn(players)

		val result = playerService.getPlayers(gameId)

		assertThat(result).isSameAs(players)
	}

	@Test
	fun `getPlayers returns null when the game doesn't exist, without consulting PlayerDao`() {
		given(gameDao.findById(gameId)).willReturn(null)

		val result = playerService.getPlayers(gameId)

		assertThat(result).isNull()
		verifyNoInteractions(playerDao)
	}
}
