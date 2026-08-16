package com.fortuneavenue.server.dao

import com.fortuneavenue.server.DatabaseTest
import com.fortuneavenue.server.models.board.db.SpaceType
import com.fortuneavenue.server.models.game.db.Game
import com.fortuneavenue.server.models.player.db.PlayerStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.uuid.Uuid

@SpringBootTest
class PlayerDaoTest : DatabaseTest() {

	@Autowired
	lateinit var playerDao: PlayerDao

	@Autowired
	lateinit var gameDao: GameDao

	@Autowired
	lateinit var boardDao: BoardDao

	@Autowired
	lateinit var userDao: UserDao

	private fun createGame(): Game {
		val boardId = boardDao.create(
			name = "board-${Uuid.random()}",
			spaceInputs = listOf(BoardDao.SpaceInput(SpaceType.BASIC)),
			pathInputs = emptyList(),
			startIndex = 0,
		).board.id.value
		return gameDao.create(boardId)
	}

	@Test
	fun `create persists a player tied to a game and a user`() {
		val game = createGame()
		val user = userDao.create("dave-${Uuid.random()}")

		val created = playerDao.create(gameId = game.id.value, userId = user.id.value)
		val found = playerDao.findById(created.id.value)

		assertThat(found).isNotNull()
		assertThat(found!!.gameId.value).isEqualTo(game.id.value)
		assertThat(found.userId?.value).isEqualTo(user.id.value)
	}

	@Test
	fun `create also persists state for the new player, with no position yet, status WAITING, and the given currentGold`() {
		val game = createGame()

		val created = playerDao.create(gameId = game.id.value, currentGold = 1500)
		val state = playerDao.findState(created.id.value)

		assertThat(state).isNotNull()
		assertThat(state!!.playerId.value).isEqualTo(created.id.value)
		assertThat(state.currentSpaceId).isNull()
		assertThat(state.status).isEqualTo(PlayerStatus.WAITING)
		assertThat(state.currentGold).isEqualTo(1500)
	}

	@Test
	fun `create allows a negative currentGold -- a player can owe more than they have on hand`() {
		val game = createGame()

		val created = playerDao.create(gameId = game.id.value, currentGold = -250)

		assertThat(playerDao.findState(created.id.value)!!.currentGold).isEqualTo(-250)
	}

	@Test
	fun `adjustGold adds a positive or negative delta to currentGold, allowing it to go negative`() {
		val player = playerDao.create(gameId = createGame().id.value, currentGold = 100)

		playerDao.adjustGold(player.id.value, -30)
		assertThat(playerDao.findState(player.id.value)!!.currentGold).isEqualTo(70)

		playerDao.adjustGold(player.id.value, -100)
		assertThat(playerDao.findState(player.id.value)!!.currentGold).isEqualTo(-30)
	}

	@Test
	fun `adjustGold returns null for a player id that does not exist`() {
		val result = playerDao.adjustGold(Uuid.random(), 10)

		assertThat(result).isNull()
	}

	@Test
	fun `findState returns null for a player id that does not exist`() {
		val result = playerDao.findState(Uuid.random())

		assertThat(result).isNull()
	}

	@Test
	fun `updateStatus changes a player's status`() {
		val player = playerDao.create(gameId = createGame().id.value)

		val updated = playerDao.updateStatus(player.id.value, PlayerStatus.READY)

		assertThat(updated).isNotNull()
		assertThat(updated!!.status).isEqualTo(PlayerStatus.READY)
		assertThat(playerDao.findState(player.id.value)!!.status).isEqualTo(PlayerStatus.READY)
	}

	@Test
	fun `updateStatus returns null for a player id that does not exist`() {
		val result = playerDao.updateStatus(Uuid.random(), PlayerStatus.READY)

		assertThat(result).isNull()
	}

	@Test
	fun `updatePosition changes a player's current space`() {
		// current_space_id has a real FK to board_spaces, so this needs an
		// actual space from a real board -- not just any UUID.
		val board = boardDao.create(
			name = "board-${Uuid.random()}",
			spaceInputs = listOf(BoardDao.SpaceInput(SpaceType.BASIC)),
			pathInputs = emptyList(),
			startIndex = 0,
		)
		val game = gameDao.create(board.board.id.value)
		val player = playerDao.create(gameId = game.id.value)
		val spaceId = board.spaces.single().id.value

		val updated = playerDao.updatePosition(player.id.value, spaceId)

		assertThat(updated).isNotNull()
		assertThat(updated!!.currentSpaceId?.value).isEqualTo(spaceId)
		assertThat(playerDao.findState(player.id.value)!!.currentSpaceId?.value).isEqualTo(spaceId)
	}

	@Test
	fun `create also persists state with an empty heldSuits`() {
		val game = createGame()

		val created = playerDao.create(gameId = game.id.value)

		assertThat(playerDao.findState(created.id.value)!!.heldSuits).isEmpty()
	}

	@Test
	fun `addHeldSuit adds a new suit`() {
		val player = playerDao.create(gameId = createGame().id.value)

		val addedFirst = playerDao.addHeldSuit(player.id.value, SpaceType.HEART)
		val addedSecond = playerDao.addHeldSuit(player.id.value, SpaceType.SPADE)

		assertThat(addedFirst).isTrue()
		assertThat(addedSecond).isTrue()
		assertThat(playerDao.findState(player.id.value)!!.heldSuits).containsExactlyInAnyOrder("HEART", "SPADE")
	}

	@Test
	fun `addHeldSuit picking up a suit already held returns false and does not duplicate it`() {
		val player = playerDao.create(gameId = createGame().id.value)
		playerDao.addHeldSuit(player.id.value, SpaceType.DIAMOND)

		val addedAgain = playerDao.addHeldSuit(player.id.value, SpaceType.DIAMOND)

		assertThat(addedAgain).isFalse()
		assertThat(playerDao.findState(player.id.value)!!.heldSuits).containsExactly("DIAMOND")
	}

	@Test
	fun `addHeldSuit returns null for a player id that does not exist`() {
		val result = playerDao.addHeldSuit(Uuid.random(), SpaceType.CLUB)

		assertThat(result).isNull()
	}

	private val allSuits = setOf(SpaceType.HEART, SpaceType.DIAMOND, SpaceType.SPADE, SpaceType.CLUB)

	@Test
	fun `clearHeldSuitsIfComplete clears every held suit once all 4 are held`() {
		val player = playerDao.create(gameId = createGame().id.value)
		allSuits.forEach { playerDao.addHeldSuit(player.id.value, it) }

		val cleared = playerDao.clearHeldSuitsIfComplete(player.id.value, allSuits)

		assertThat(cleared).isTrue()
		assertThat(playerDao.findState(player.id.value)!!.heldSuits).isEmpty()
	}

	@Test
	fun `clearHeldSuitsIfComplete leaves held suits untouched when even one is missing`() {
		val player = playerDao.create(gameId = createGame().id.value)
		playerDao.addHeldSuit(player.id.value, SpaceType.HEART)
		playerDao.addHeldSuit(player.id.value, SpaceType.DIAMOND)
		playerDao.addHeldSuit(player.id.value, SpaceType.SPADE)

		val cleared = playerDao.clearHeldSuitsIfComplete(player.id.value, allSuits)

		assertThat(cleared).isFalse()
		assertThat(playerDao.findState(player.id.value)!!.heldSuits).containsExactlyInAnyOrder("HEART", "DIAMOND", "SPADE")
	}

	@Test
	fun `clearHeldSuitsIfComplete returns null for a player id that does not exist`() {
		val result = playerDao.clearHeldSuitsIfComplete(Uuid.random(), allSuits)

		assertThat(result).isNull()
	}

	@Test
	fun `create persists a player with no user, for a future computer opponent`() {
		val game = createGame()

		val created = playerDao.create(gameId = game.id.value)

		assertThat(created.gameId.value).isEqualTo(game.id.value)
		assertThat(created.userId).isNull()
	}

	@Test
	fun `findByGameId returns only players belonging to that game`() {
		val gameOne = createGame()
		val gameTwo = createGame()

		val playerInGameOne = playerDao.create(gameId = gameOne.id.value)
		playerDao.create(gameId = gameTwo.id.value)

		val players = playerDao.findByGameId(gameOne.id.value)

		assertThat(players.map { it.id.value }).containsExactly(playerInGameOne.id.value)
	}

	@Test
	fun `findById returns null for an id that does not exist`() {
		val result = playerDao.findById(Uuid.random())

		assertThat(result).isNull()
	}
}
