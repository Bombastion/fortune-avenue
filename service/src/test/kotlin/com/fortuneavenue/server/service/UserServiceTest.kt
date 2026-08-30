package com.fortuneavenue.server.service

import com.fortuneavenue.server.dao.UserDao
import com.fortuneavenue.server.models.common.rest.SortDirection
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
import org.mockito.Mockito.verifyNoInteractions
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

    // --- listUsers ---

    @Test
    fun `listUsers fails when page is negative`() {
        val result = userService.listUsers(page = -1, pageSize = 10)

        assertThat(result.exceptionOrNull()).isInstanceOf(InvalidUserException::class.java)
        verifyNoInteractions(userDao)
    }

    @Test
    fun `listUsers fails when pageSize is less than 1`() {
        val result = userService.listUsers(page = 0, pageSize = 0)

        assertThat(result.exceptionOrNull()).isInstanceOf(InvalidUserException::class.java)
        verifyNoInteractions(userDao)
    }

    @Test
    fun `listUsers defaults to ascending order and returns the requested page's metadata`() {
        val users = listOf(mock(User::class.java), mock(User::class.java))
        given(userDao.findPage(page = 0, pageSize = 2, ascending = true)).willReturn(users)
        given(userDao.count()).willReturn(5L)

        val result = userService.listUsers(page = 0, pageSize = 2)

        val page = result.getOrNull()
        assertThat(page).isNotNull()
        assertThat(page!!.items).isEqualTo(users)
        assertThat(page.page).isEqualTo(0)
        assertThat(page.pageSize).isEqualTo(2)
        assertThat(page.direction).isEqualTo(SortDirection.ASC)
        // 5 users at 2 per page is 3 pages (2 full pages + a partial third).
        assertThat(page.totalPages).isEqualTo(3)
    }

    @Test
    fun `listUsers passes descending order through to the DAO`() {
        given(userDao.findPage(page = 1, pageSize = 3, ascending = false)).willReturn(emptyList())
        given(userDao.count()).willReturn(0L)

        val result = userService.listUsers(page = 1, pageSize = 3, direction = SortDirection.DESC)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.direction).isEqualTo(SortDirection.DESC)
        assertThat(result.getOrNull()?.totalPages).isEqualTo(0)
        verify(userDao).findPage(page = 1, pageSize = 3, ascending = false)
    }

    @Test
    fun `listUsers reports exactly one page when everything fits within pageSize`() {
        given(userDao.findPage(page = 0, pageSize = 50, ascending = true)).willReturn(emptyList())
        given(userDao.count()).willReturn(3L)

        val result = userService.listUsers(page = 0, pageSize = 50)

        assertThat(result.getOrNull()?.totalPages).isEqualTo(1)
    }
}
