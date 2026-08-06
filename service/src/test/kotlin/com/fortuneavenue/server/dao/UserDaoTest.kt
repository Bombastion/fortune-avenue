package com.fortuneavenue.server.dao

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.uuid.Uuid

/**
 * Exercises UserDao directly against a real database, with no HTTP layer
 * or UserService involved — so this keeps working (and keeps failing
 * loudly) even if the controller or service change independently.
 */
@SpringBootTest
class UserDaoTest {

	@Autowired
	lateinit var userDao: UserDao

	@Test
	fun `create persists a user that can then be found by id`() {
		val username = "carol-${Uuid.random()}"

		val created = userDao.create(username)
		val found = userDao.findById(created.id.value)

		assertThat(found).isNotNull()
		assertThat(found!!.id.value).isEqualTo(created.id.value)
		assertThat(found.username).isEqualTo(username)
	}

	@Test
	fun `findById returns null for an id that does not exist`() {
		val result = userDao.findById(Uuid.random())

		assertThat(result).isNull()
	}
}
