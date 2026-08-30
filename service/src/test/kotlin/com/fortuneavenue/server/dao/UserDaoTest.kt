package com.fortuneavenue.server.dao

import com.fortuneavenue.server.DatabaseTest
import kotlin.uuid.Uuid
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Exercises UserDao directly against a real database, with no HTTP layer or UserService involved —
 * so this keeps working (and keeps failing loudly) even if the controller or service change
 * independently.
 */
@SpringBootTest
class UserDaoTest : DatabaseTest() {

    @Autowired lateinit var userDao: UserDao

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

    private fun createUserWithUsername(username: String) = userDao.create(username)

    @Test
    fun `findPage sorts users by username ascending by default`() {
        listOf("carol", "alice", "bob").forEach { createUserWithUsername(it) }

        val page = userDao.findPage(page = 0, pageSize = 10, ascending = true)

        assertThat(page.map { it.username }).containsExactly("alice", "bob", "carol")
    }

    @Test
    fun `findPage sorts descending when ascending is false`() {
        listOf("carol", "alice", "bob").forEach { createUserWithUsername(it) }

        val page = userDao.findPage(page = 0, pageSize = 10, ascending = false)

        assertThat(page.map { it.username }).containsExactly("carol", "bob", "alice")
    }

    @Test
    fun `findPage never returns more users than pageSize`() {
        repeat(3) { createUserWithUsername("user-$it") }

        val page = userDao.findPage(page = 0, pageSize = 1, ascending = true)

        assertThat(page).hasSize(1)
    }

    @Test
    fun `findPage slices users across pages without overlap`() {
        listOf("alice", "bob", "carol").forEach { createUserWithUsername(it) }

        val firstPage = userDao.findPage(page = 0, pageSize = 2, ascending = true)
        val secondPage = userDao.findPage(page = 1, pageSize = 2, ascending = true)

        assertThat(firstPage.map { it.username }).containsExactly("alice", "bob")
        assertThat(secondPage.map { it.username }).containsExactly("carol")
    }

    @Test
    fun `findPage returns an empty list once past the last page`() {
        createUserWithUsername("only-user-${Uuid.random()}")

        val page = userDao.findPage(page = 1, pageSize = 10, ascending = true)

        assertThat(page).isEmpty()
    }

    @Test
    fun `count reflects exactly how many users exist`() {
        assertThat(userDao.count()).isZero()

        repeat(3) { createUserWithUsername("user-count-$it") }

        assertThat(userDao.count()).isEqualTo(3)
    }
}
