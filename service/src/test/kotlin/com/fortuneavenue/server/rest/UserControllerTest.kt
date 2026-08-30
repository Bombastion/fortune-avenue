package com.fortuneavenue.server.rest

import com.fortuneavenue.server.DatabaseTest
import com.fortuneavenue.server.models.common.rest.ErrorResponse
import com.fortuneavenue.server.models.common.rest.Page
import com.fortuneavenue.server.models.common.rest.SortDirection
import com.fortuneavenue.server.models.user.rest.CreateUserRequest
import com.fortuneavenue.server.models.user.rest.UserResponse
import kotlin.uuid.Uuid
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.getForEntity
import org.springframework.boot.test.web.client.postForEntity
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class UserControllerTest : DatabaseTest() {

    @Autowired lateinit var restTemplate: TestRestTemplate

    @Test
    fun `creating a user returns the created user as JSON`() {
        val username = "alice-${Uuid.random()}"

        val response =
            restTemplate.postForEntity<UserResponse>(
                "/users",
                CreateUserRequest(username),
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
        val body = response.body
        assertThat(body).isNotNull()
        assertThat(body!!.username).isEqualTo(username)
        assertThat(body.id).isNotBlank()
    }

    @Test
    fun `retrieving a user by id returns the same user as JSON`() {
        val username = "bob-${Uuid.random()}"
        val created =
            restTemplate
                .postForEntity<UserResponse>(
                    "/users",
                    CreateUserRequest(username),
                )
                .body!!

        val response = restTemplate.getForEntity<UserResponse>("/users/${created.id}")

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isEqualTo(created)
    }

    @Test
    fun `retrieving an unknown user id returns 404`() {
        val response = restTemplate.getForEntity<String>("/users/${Uuid.random()}")

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `retrieving a malformed user id returns 400`() {
        val response = restTemplate.getForEntity<String>("/users/not-a-uuid")

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    private fun listUsersPage(query: String): ResponseEntity<Page<UserResponse>> =
        restTemplate.exchange(
            "/users$query",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<Page<UserResponse>>() {},
        )

    private fun createUsers(usernames: List<String>) =
        usernames.shuffled().forEach { username ->
            restTemplate.postForEntity<UserResponse>("/users", CreateUserRequest(username))
        }

    @Test
    fun `listing users sorts by username ascending by default`() {
        createUsers(listOf("carol", "alice", "bob"))

        val response = listUsersPage("?page=0&pageSize=1000")

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        val body = response.body!!
        assertThat(body.page).isEqualTo(0)
        assertThat(body.pageSize).isEqualTo(1000)
        assertThat(body.direction).isEqualTo(SortDirection.ASC)
        assertThat(body.totalPages).isEqualTo(1)
        assertThat(body.items.map { it.username }).containsExactly("alice", "bob", "carol")
    }

    @Test
    fun `listing users with direction DESC reverses the sort`() {
        createUsers(listOf("carol", "alice", "bob"))

        val response = listUsersPage("?page=0&pageSize=1000&direction=DESC")

        assertThat(response.body!!.items.map { it.username })
            .containsExactly("carol", "bob", "alice")
    }

    @Test
    fun `listing users paginates across pages`() {
        createUsers(listOf("alice", "bob", "carol"))

        val firstPage = listUsersPage("?page=0&pageSize=2")
        val secondPage = listUsersPage("?page=1&pageSize=2")

        assertThat(firstPage.body!!.items.map { it.username }).containsExactly("alice", "bob")
        assertThat(firstPage.body!!.totalPages).isEqualTo(2)
        assertThat(secondPage.body!!.items.map { it.username }).containsExactly("carol")
    }

    @Test
    fun `listing users with a negative page returns 400`() {
        val response = restTemplate.getForEntity<ErrorResponse>("/users?page=-1")

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.message).isNotBlank()
    }

    @Test
    fun `listing users with a pageSize less than 1 returns 400`() {
        val response = restTemplate.getForEntity<ErrorResponse>("/users?pageSize=0")

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.message).isNotBlank()
    }

    @Test
    fun `listing users with an invalid direction returns 400`() {
        val response = restTemplate.getForEntity<ErrorResponse>("/users?direction=sideways")

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.message).isNotBlank()
    }
}
