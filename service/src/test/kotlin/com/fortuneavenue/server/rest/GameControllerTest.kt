package com.fortuneavenue.server.rest

import com.fortuneavenue.server.models.game.rest.GameResponse
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
class GameControllerTest {

	@Autowired
	lateinit var restTemplate: TestRestTemplate

	@Test
	fun `creating a game returns it as JSON`() {
		val response = restTemplate.postForEntity<GameResponse>("/games", null)

		assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
		assertThat(response.body?.id).isNotBlank()
	}

	@Test
	fun `retrieving a game by id returns the same game as JSON`() {
		val created = restTemplate.postForEntity<GameResponse>("/games", null).body!!

		val response = restTemplate.getForEntity<GameResponse>("/games/${created.id}")

		assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
		assertThat(response.body).isEqualTo(created)
	}

	@Test
	fun `retrieving an unknown game id returns 404`() {
		val response = restTemplate.getForEntity<String>("/games/${Uuid.random()}")

		assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
	}

	@Test
	fun `retrieving a malformed game id returns 400`() {
		val response = restTemplate.getForEntity<String>("/games/not-a-uuid")

		assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
	}
}
