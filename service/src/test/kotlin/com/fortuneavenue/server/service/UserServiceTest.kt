package com.fortuneavenue.server.service

import com.fortuneavenue.server.dao.UserDao
import com.fortuneavenue.server.models.user.db.User
import kotlin.uuid.Uuid
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension

/**
 * Exercises UserService with a mocked UserDao — no Spring context and no database involved, so this
 * stays fast and keeps working even if the controller or the DAO's persistence details change
 * independently.
 */
@ExtendWith(MockitoExtension::class)
class UserServiceTest {

    @Mock lateinit var userDao: UserDao

    private lateinit var userService: UserService

    @BeforeEach
    fun setUp() {
        userService = UserService(userDao)
    }

    @Test
    fun `createUser delegates to the DAO and returns its result`() {
        val username = "alice"
        val createdUser = mock(User::class.java)
        given(userDao.create(username)).willReturn(createdUser)

        val result = userService.createUser(username)

        assertThat(result).isSameAs(createdUser)
        verify(userDao).create(username)
    }

    @Test
    fun `getUser delegates to the DAO and returns its result when found`() {
        val id = Uuid.random()
        val foundUser = mock(User::class.java)
        given(userDao.findById(id)).willReturn(foundUser)

        val result = userService.getUser(id)

        assertThat(result).isSameAs(foundUser)
        verify(userDao).findById(id)
    }

    @Test
    fun `getUser returns null when the DAO finds nothing`() {
        val id = Uuid.random()
        given(userDao.findById(id)).willReturn(null)

        val result = userService.getUser(id)

        assertThat(result).isNull()
    }
}
