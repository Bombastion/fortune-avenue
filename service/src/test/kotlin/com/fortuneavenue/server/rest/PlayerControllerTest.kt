package com.fortuneavenue.server.rest

import com.fortuneavenue.server.DatabaseTest
import com.fortuneavenue.server.models.board.db.SpaceType
import com.fortuneavenue.server.models.board.rest.BoardResponse
import com.fortuneavenue.server.models.board.rest.CreateBoardPathRequest
import com.fortuneavenue.server.models.board.rest.CreateBoardRequest
import com.fortuneavenue.server.models.board.rest.CreateBoardSpaceRequest
import com.fortuneavenue.server.models.common.rest.ErrorResponse
import com.fortuneavenue.server.models.game.rest.CreateGameRequest
import com.fortuneavenue.server.models.game.rest.GameResponse
import com.fortuneavenue.server.models.player.rest.AddPlayerRequest
import com.fortuneavenue.server.models.player.rest.PlayerResponse
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
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import kotlin.uuid.Uuid

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class PlayerControllerTest : DatabaseTest() {

	@Autowired
	lateinit var restTemplate: TestRestTemplate

	private fun createBoard(): BoardResponse = restTemplate.postForEntity<BoardResponse>(
		"/boards",
		CreateBoardRequest(
			name = "loop-${Uuid.random()}",
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
			startingGold = 1000,
			baseSalary = 200,
			promotionBonus = 50,
		),
	).body!!

	private fun createGame(): GameResponse = restTemplate.postForEntity<GameResponse>(
		"/games",
		CreateGameRequest(boardId = createBoard().id),
	).body!!

	private fun createUser(): UserResponse = restTemplate.postForEntity<UserResponse>(
		"/users",
		CreateUserRequest("player-${Uuid.random()}"),
	).body!!

	private fun getPlayers(gameId: String) = restTemplate.exchange(
		"/games/$gameId/players",
		HttpMethod.GET,
		HttpEntity.EMPTY,
		object : ParameterizedTypeReference<List<PlayerResponse>>() {},
	)

	@Test
	fun `adding a player with no userId seats a player with no user`() {
		val game = createGame()

		val response = restTemplate.postForEntity<PlayerResponse>(
			"/games/${game.id}/players",
			AddPlayerRequest(),
		)

		assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
		val body = response.body
		assertThat(body).isNotNull()
		assertThat(body!!.gameId).isEqualTo(game.id)
		assertThat(body.userId).isNull()
	}

	@Test
	fun `adding a player with a real userId ties the player to that user`() {
		val game = createGame()
		val user = createUser()

		val response = restTemplate.postForEntity<PlayerResponse>(
			"/games/${game.id}/players",
			AddPlayerRequest(userId = user.id),
		)

		assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
		val body = response.body
		assertThat(body).isNotNull()
		assertThat(body!!.gameId).isEqualTo(game.id)
		assertThat(body.userId).isEqualTo(user.id)
	}

	@Test
	fun `adding a player with a userId that doesn't exist returns 400 with an explanatory message`() {
		val game = createGame()

		val response = restTemplate.postForEntity<ErrorResponse>(
			"/games/${game.id}/players",
			AddPlayerRequest(userId = Uuid.random().toString()),
		)

		assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
		assertThat(response.body?.message).isNotBlank()
	}

	@Test
	fun `adding the same user to a game twice returns 400 the second time`() {
		val game = createGame()
		val user = createUser()

		restTemplate.postForEntity<PlayerResponse>("/games/${game.id}/players", AddPlayerRequest(userId = user.id))
		val response = restTemplate.postForEntity<ErrorResponse>(
			"/games/${game.id}/players",
			AddPlayerRequest(userId = user.id),
		)

		assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
		assertThat(response.body?.message).isNotBlank()
	}

	@Test
	fun `adding a player to an unknown game returns 404`() {
		val response = restTemplate.postForEntity<String>(
			"/games/${Uuid.random()}/players",
			AddPlayerRequest(),
		)

		assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
	}

	@Test
	fun `adding a player to a malformed game id returns 400`() {
		val response = restTemplate.postForEntity<String>(
			"/games/not-a-uuid/players",
			AddPlayerRequest(),
		)

		assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
	}

	@Test
	fun `listing players returns everyone seated in that game`() {
		val game = createGame()
		val user = createUser()
		restTemplate.postForEntity<PlayerResponse>("/games/${game.id}/players", AddPlayerRequest())
		restTemplate.postForEntity<PlayerResponse>("/games/${game.id}/players", AddPlayerRequest(userId = user.id))

		val response = getPlayers(game.id)

		assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
		assertThat(response.body).hasSize(2)
		assertThat(response.body!!.map { it.userId }).containsExactlyInAnyOrder(null, user.id)
	}

	@Test
	fun `listing players for an unknown game returns 404`() {
		val response = restTemplate.exchange(
			"/games/${Uuid.random()}/players",
			HttpMethod.GET,
			HttpEntity.EMPTY,
			String::class.java,
		)

		assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
	}
}
