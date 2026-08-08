package com.fortuneavenue.server.service

import com.fortuneavenue.server.dao.PlayerDao
import com.fortuneavenue.server.dao.UserDao
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

	@BeforeEach
	fun setUp() {
		playerService = PlayerService(playerDao, userDao)
	}

	@Test
	fun `addPlayer with no userId persists a player with no user, without consulting UserDao`() {
		val createdPlayer = mock(Player::class.java)
		given(playerDao.create(gameId, null)).willReturn(createdPlayer)

		val result = playerService.addPlayer(gameId, null)

		assertThat(result.isSuccess).isTrue()
		assertThat(result.getOrNull()).isSameAs(createdPlayer)
		verifyNoInteractions(userDao)
	}

	@Test
	fun `addPlayer with a real, not-yet-seated userId persists the player`() {
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
		val userId = Uuid.random()
		given(userDao.findById(userId)).willReturn(null)

		val result = playerService.addPlayer(gameId, userId)

		assertThat(result.isFailure).isTrue()
		assertThat(result.exceptionOrNull()).isInstanceOf(InvalidPlayerException::class.java)
	}

	@Test
	fun `addPlayer rejects a user who is already seated in the game`() {
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
	fun `getPlayers delegates to the DAO`() {
		val players = listOf(mock(Player::class.java), mock(Player::class.java))
		given(playerDao.findByGameId(gameId)).willReturn(players)

		val result = playerService.getPlayers(gameId)

		assertThat(result).isSameAs(players)
	}
}
