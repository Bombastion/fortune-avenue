package com.fortuneavenue.server.service

import com.fortuneavenue.server.dao.PlayerDao
import com.fortuneavenue.server.dao.UserDao
import com.fortuneavenue.server.models.player.db.Player
import org.springframework.stereotype.Service
import kotlin.uuid.Uuid

/**
 * Whether [gameId] itself refers to a real game is deliberately not this
 * class's concern -- that's part of the URL in the REST layer (/games/{id}/players),
 * so a missing game is a 404 the controller decides, not a validation failure
 * that belongs in this Result. What *is* validated here is the player being
 * added: an explicit userId has to point at a real user, and that user can't
 * already be seated in this game.
 */
@Service
class PlayerService(
	private val playerDao: PlayerDao,
	private val userDao: UserDao,
) {

	fun addPlayer(gameId: Uuid, userId: Uuid?): Result<Player> {
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

	fun getPlayers(gameId: Uuid): List<Player> = playerDao.findByGameId(gameId)
}
