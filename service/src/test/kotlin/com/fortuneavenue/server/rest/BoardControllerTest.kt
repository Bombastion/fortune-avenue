package com.fortuneavenue.server.rest

import com.fortuneavenue.server.models.board.db.SpaceType
import com.fortuneavenue.server.models.board.rest.BoardResponse
import com.fortuneavenue.server.models.board.rest.CreateBoardPathRequest
import com.fortuneavenue.server.models.board.rest.CreateBoardRequest
import com.fortuneavenue.server.models.board.rest.CreateBoardSpaceRequest
import com.fortuneavenue.server.models.common.rest.ErrorResponse
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
class BoardControllerTest {

	@Autowired
	lateinit var restTemplate: TestRestTemplate

	private fun validRequest(name: String) = CreateBoardRequest(
		name = name,
		spaces = listOf(
			CreateBoardSpaceRequest(SpaceType.BASIC),
			CreateBoardSpaceRequest(SpaceType.BASIC),
			CreateBoardSpaceRequest(SpaceType.BASIC),
		),
		paths = listOf(
			CreateBoardPathRequest(0, 1),
			CreateBoardPathRequest(1, 2),
			CreateBoardPathRequest(2, 0),
		),
		startSpaceIndex = 0,
	)

	@Test
	fun `creating a valid board returns it as JSON`() {
		val request = validRequest("loop-${Uuid.random()}")

		val response = restTemplate.postForEntity<BoardResponse>("/boards", request)

		assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
		val body = response.body
		assertThat(body).isNotNull()
		assertThat(body!!.name).isEqualTo(request.name)
		assertThat(body.spaces).hasSize(3)
		assertThat(body.paths).hasSize(3)
		assertThat(body.spaces.map { it.id }).contains(body.startSpaceId)
	}

	@Test
	fun `creating an invalid board returns 400 with an explanatory message`() {
		// space index 2 is declared but nothing makes it reachable from start
		val request = validRequest("invalid-${Uuid.random()}").copy(
			paths = listOf(CreateBoardPathRequest(0, 1)),
		)

		val response = restTemplate.postForEntity<ErrorResponse>("/boards", request)

		assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
		assertThat(response.body?.message).isNotBlank()
	}

	@Test
	fun `retrieving a board by id returns the same board as JSON`() {
		val created = restTemplate.postForEntity<BoardResponse>(
			"/boards",
			validRequest("fetch-me-${Uuid.random()}"),
		).body!!

		val response = restTemplate.getForEntity<BoardResponse>("/boards/${created.id}")

		assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
		assertThat(response.body).isEqualTo(created)
	}

	@Test
	fun `retrieving an unknown board id returns 404`() {
		val response = restTemplate.getForEntity<String>("/boards/${Uuid.random()}")

		assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
	}

	@Test
	fun `retrieving a malformed board id returns 400`() {
		val response = restTemplate.getForEntity<String>("/boards/not-a-uuid")

		assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
	}
}
