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
import kotlin.uuid.Uuid
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.getForEntity
import org.springframework.boot.test.web.client.postForEntity
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class GameControllerTest : DatabaseTest() {

    @Autowired lateinit var restTemplate: TestRestTemplate

    private fun createBoard(): BoardResponse =
        restTemplate
            .postForEntity<BoardResponse>(
                "/boards",
                CreateBoardRequest(
                    name = "loop-${Uuid.random()}",
                    // Spaces 3-7 are the required BANK + one-of-each-suit spaces every board must
                    // have (see
                    // RequiredSpaceTypesValidator) -- this board is only ever used to seed a
                    // game/player, so
                    // their exact positions don't matter here.
                    spaces =
                        listOf(
                            CreateBoardSpaceRequest(SpaceType.BASIC),
                            CreateBoardSpaceRequest(SpaceType.BASIC),
                            CreateBoardSpaceRequest(SpaceType.BASIC),
                            CreateBoardSpaceRequest(SpaceType.BANK),
                            CreateBoardSpaceRequest(SpaceType.HEART),
                            CreateBoardSpaceRequest(SpaceType.DIAMOND),
                            CreateBoardSpaceRequest(SpaceType.SPADE),
                            CreateBoardSpaceRequest(SpaceType.CLUB),
                        ),
                    paths =
                        listOf(
                            CreateBoardPathRequest(0, 1),
                            CreateBoardPathRequest(1, 2),
                            CreateBoardPathRequest(2, 3),
                            CreateBoardPathRequest(3, 4),
                            CreateBoardPathRequest(4, 5),
                            CreateBoardPathRequest(5, 6),
                            CreateBoardPathRequest(6, 7),
                            CreateBoardPathRequest(7, 0),
                        ),
                    startSpaceIndex = 0,
                    startingGold = 1000,
                    baseSalary = 200,
                    promotionBonus = 50,
                ),
            )
            .body!!

    @Test
    fun `creating a game for a real board returns it as JSON`() {
        val board = createBoard()

        val response =
            restTemplate.postForEntity<GameResponse>(
                "/games",
                CreateGameRequest(boardId = board.id),
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
        val body = response.body
        assertThat(body).isNotNull()
        assertThat(body!!.boardId).isEqualTo(board.id)
        assertThat(body.id).isNotBlank()
    }

    @Test
    fun `creating a game without a targetNetWorth defaults it to 6000 gold`() {
        val board = createBoard()

        val response =
            restTemplate.postForEntity<GameResponse>(
                "/games",
                CreateGameRequest(boardId = board.id),
            )

        assertThat(response.body?.targetNetWorth).isEqualTo(6000)
    }

    @Test
    fun `creating a game with a targetNetWorth persists it`() {
        val board = createBoard()

        val response =
            restTemplate.postForEntity<GameResponse>(
                "/games",
                CreateGameRequest(boardId = board.id, targetNetWorth = 9000),
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(response.body?.targetNetWorth).isEqualTo(9000)
    }

    @Test
    fun `creating a game with a non-positive targetNetWorth returns 400 with an explanatory message`() {
        val board = createBoard()

        val response =
            restTemplate.postForEntity<ErrorResponse>(
                "/games",
                CreateGameRequest(boardId = board.id, targetNetWorth = 0),
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.message).isNotBlank()
    }

    @Test
    fun `creating a game without a maxTurns defaults it to 10`() {
        val board = createBoard()

        val response =
            restTemplate.postForEntity<GameResponse>(
                "/games",
                CreateGameRequest(boardId = board.id),
            )

        assertThat(response.body?.maxTurns).isEqualTo(10)
    }

    @Test
    fun `creating a game with a maxTurns persists it`() {
        val board = createBoard()

        val response =
            restTemplate.postForEntity<GameResponse>(
                "/games",
                CreateGameRequest(boardId = board.id, maxTurns = 25),
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(response.body?.maxTurns).isEqualTo(25)
    }

    @Test
    fun `creating a game with a non-positive maxTurns returns 400 with an explanatory message`() {
        val board = createBoard()

        val response =
            restTemplate.postForEntity<ErrorResponse>(
                "/games",
                CreateGameRequest(boardId = board.id, maxTurns = 0),
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.message).isNotBlank()
    }

    @Test
    fun `creating a game for a board that doesn't exist returns 400 with an explanatory message`() {
        val response =
            restTemplate.postForEntity<ErrorResponse>(
                "/games",
                CreateGameRequest(boardId = Uuid.random().toString()),
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.message).isNotBlank()
    }

    @Test
    fun `creating a game with a malformed boardId returns 400 with an explanatory message`() {
        val response =
            restTemplate.postForEntity<ErrorResponse>(
                "/games",
                CreateGameRequest(boardId = "not-a-uuid"),
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.message).isNotBlank()
    }

    @Test
    fun `retrieving a game by id returns the same game as JSON`() {
        val board = createBoard()
        val created =
            restTemplate
                .postForEntity<GameResponse>("/games", CreateGameRequest(boardId = board.id))
                .body!!

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
