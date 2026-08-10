package com.fortuneavenue.server.rest

import com.fortuneavenue.server.DatabaseTest
import com.fortuneavenue.server.models.board.db.SpaceType
import com.fortuneavenue.server.models.board.rest.BoardResponse
import com.fortuneavenue.server.models.board.rest.CreateBoardPathRequest
import com.fortuneavenue.server.models.board.rest.CreateBoardRequest
import com.fortuneavenue.server.models.board.rest.CreateBoardSpaceRequest
import com.fortuneavenue.server.models.common.rest.ErrorResponse
import com.fortuneavenue.server.models.common.rest.Page
import com.fortuneavenue.server.models.common.rest.SortDirection
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
import kotlin.uuid.Uuid

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class BoardControllerTest : DatabaseTest() {

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

	private fun listBoardsPage(query: String): ResponseEntity<Page<BoardResponse>> = restTemplate.exchange(
		"/boards$query",
		HttpMethod.GET,
		null,
		object : ParameterizedTypeReference<Page<BoardResponse>>() {},
	)

	private fun createBoards(names: List<String>) = names.shuffled().forEach { name ->
		restTemplate.postForEntity<BoardResponse>("/boards", validRequest(name))
	}

	@Test
	fun `listing boards sorts by name ascending by default`() {
		createBoards(listOf("c", "a", "b"))

		val response = listBoardsPage("?page=0&pageSize=1000")

		assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
		val body = response.body!!
		assertThat(body.page).isEqualTo(0)
		assertThat(body.pageSize).isEqualTo(1000)
		assertThat(body.direction).isEqualTo(SortDirection.ASC)
		assertThat(body.totalPages).isEqualTo(1)
		assertThat(body.items.map { it.name }).containsExactly("a", "b", "c")
	}

	@Test
	fun `listing boards with direction DESC reverses the sort`() {
		createBoards(listOf("c", "a", "b"))

		val response = listBoardsPage("?page=0&pageSize=1000&direction=DESC")

		assertThat(response.body!!.items.map { it.name }).containsExactly("c", "b", "a")
	}

	@Test
	fun `listing boards paginates across pages`() {
		createBoards(listOf("a", "b", "c"))

		val firstPage = listBoardsPage("?page=0&pageSize=2")
		val secondPage = listBoardsPage("?page=1&pageSize=2")

		assertThat(firstPage.body!!.items.map { it.name }).containsExactly("a", "b")
		assertThat(firstPage.body!!.totalPages).isEqualTo(2)
		assertThat(secondPage.body!!.items.map { it.name }).containsExactly("c")
	}

	@Test
	fun `listing boards with a negative page returns 400`() {
		val response = restTemplate.getForEntity<ErrorResponse>("/boards?page=-1")

		assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
		assertThat(response.body?.message).isNotBlank()
	}

	@Test
	fun `listing boards with a pageSize less than 1 returns 400`() {
		val response = restTemplate.getForEntity<ErrorResponse>("/boards?pageSize=0")

		assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
		assertThat(response.body?.message).isNotBlank()
	}

	@Test
	fun `listing boards with an invalid direction returns 400`() {
		val response = restTemplate.getForEntity<ErrorResponse>("/boards?direction=sideways")

		assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
		assertThat(response.body?.message).isNotBlank()
	}
}
