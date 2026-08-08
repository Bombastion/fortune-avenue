package com.fortuneavenue.server.dao

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.uuid.Uuid

@SpringBootTest
class PlayerDaoTest {

	@Autowired
	lateinit var playerDao: PlayerDao

	@Autowired
	lateinit var gameDao: GameDao

	@Autowired
	lateinit var userDao: UserDao

	@Test
	fun `create persists a player tied to a game and a user`() {
		val game = gameDao.create()
		val user = userDao.create("dave-${Uuid.random()}")

		val created = playerDao.create(gameId = game.id.value, userId = user.id.value)
		val found = playerDao.findById(created.id.value)

		assertThat(found).isNotNull()
		assertThat(found!!.gameId.value).isEqualTo(game.id.value)
		assertThat(found.userId?.value).isEqualTo(user.id.value)
	}

	@Test
	fun `create persists a player with no user, for a future computer opponent`() {
		val game = gameDao.create()

		val created = playerDao.create(gameId = game.id.value)

		assertThat(created.gameId.value).isEqualTo(game.id.value)
		assertThat(created.userId).isNull()
	}

	@Test
	fun `findByGameId returns only players belonging to that game`() {
		val gameOne = gameDao.create()
		val gameTwo = gameDao.create()

		val playerInGameOne = playerDao.create(gameId = gameOne.id.value)
		playerDao.create(gameId = gameTwo.id.value)

		val players = playerDao.findByGameId(gameOne.id.value)

		assertThat(players.map { it.id.value }).containsExactly(playerInGameOne.id.value)
	}

	@Test
	fun `findById returns null for an id that does not exist`() {
		val result = playerDao.findById(Uuid.random())

		assertThat(result).isNull()
	}
}
