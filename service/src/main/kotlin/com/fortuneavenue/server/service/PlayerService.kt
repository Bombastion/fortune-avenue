package com.fortuneavenue.server.service

import com.fortuneavenue.server.dao.GameDao
import com.fortuneavenue.server.dao.PlayerDao
import com.fortuneavenue.server.dao.UserDao
import com.fortuneavenue.server.models.player.db.Player
import org.springframework.stereotype.Service
import kotlin.uuid.Uuid

/**
 * All validation lives here rather than in PlayerController, specifically so
 * that "does this game exist" and "is this player valid" behave the same way
 * no matter who calls this service -- REST today, a WebSocket handler or
 * anything else later -- rather than each caller having to re-implement
 * (and potentially get slightly wrong) the same checks.
 */
@Service
class PlayerService(
	private val playerDao: PlayerDao,
	private val gameDao: GameDao,
	private val userDao: UserDao,
) {

	fun addPlayer(gameId: Uuid, userId: Uuid?): Result<Player> {
		if (gameDao.findById(gameId) == null) {
			return Result.failure(GameNotFoundException("Game $gameId does not exist."))
		}

		if (userId != null) {
			if (userDao.findById(userId) == null) {
				return Result.failure(InvalidPlayerException("User $userId does not exist."))
			}

			val alreadySeated = playerDao.findByGameId(gameId).any { it.userId?.value == userId }
			if (alreadySeated) {
				return Result.failure(InvalidPlayerException("User $userId is already a player in game $gameId."))
			}
		}

		return Result.success(playerDao.create(gameId, userId))
	}

	/** Returns null if [gameId] doesn't refer to a real game, an empty list if it does but has no players yet. */
	fun getPlayers(gameId: Uuid): List<Player>? {
		if (gameDao.findById(gameId) == null) return null

		return playerDao.findByGameId(gameId)
	}
}
