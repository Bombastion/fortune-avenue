package com.fortuneavenue.server.dao

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.uuid.Uuid

@SpringBootTest
class GameDaoTest {

	@Autowired
	lateinit var gameDao: GameDao

	@Test
	fun `create persists a game that can then be found by id`() {
		val created = gameDao.create()

		val found = gameDao.findById(created.id.value)

		assertThat(found).isNotNull()
		assertThat(found!!.id.value).isEqualTo(created.id.value)
	}

	@Test
	fun `findById returns null for an id that does not exist`() {
		val result = gameDao.findById(Uuid.random())

		assertThat(result).isNull()
	}
}
