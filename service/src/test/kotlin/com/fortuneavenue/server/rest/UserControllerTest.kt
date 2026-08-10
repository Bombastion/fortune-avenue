package com.fortuneavenue.server.rest

import com.fortuneavenue.server.DatabaseTest
import com.fortuneavenue.server.models.user.rest.CreateUserRequest
import com.fortuneavenue.server.models.user.rest.UserResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.getForEntity
import org.springframework.boot.test.web.client.postForEntity
import org.springframework.http.HttpStatus
import kotlin.uuid.Uuid

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class UserControllerTest : DatabaseTest() {

	@Autowired
	lateinit var restTemplate: TestRestTemplate

	@Test
	fun `creating a user returns the created user as JSON`() {
		val username = "alice-${Uuid.random()}"

		val response = restTemplate.postForEntity<UserResponse>(
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
		val created = restTemplate.postForEntity<UserResponse>(
			"/users",
			CreateUserRequest(username),
        ).body!!

		val response = restTemplate.getForEntity<UserResponse>(
			"/users/${created.id}",
        )

		assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
		assertThat(response.body).isEqualTo(created)
	}

	@Test
	fun `retrieving an unknown user id returns 404`() {
		val response = restTemplate.getForEntity<String>(
			"/users/${Uuid.random()}",
        )

		assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
	}

	@Test
	fun `retrieving a malformed user id returns 400`() {
		val response = restTemplate.getForEntity<String>(
			"/users/not-a-uuid",
        )

		assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
	}
}
