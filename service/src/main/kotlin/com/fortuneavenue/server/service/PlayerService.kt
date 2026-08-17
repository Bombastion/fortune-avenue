package com.fortuneavenue.server.service

import com.fortuneavenue.server.dao.BoardDao
import com.fortuneavenue.server.dao.GameDao
import com.fortuneavenue.server.dao.PlayerDao
import com.fortuneavenue.server.dao.UserDao
import com.fortuneavenue.server.models.player.db.Player
import kotlin.uuid.Uuid
import org.springframework.stereotype.Service

@Service
class PlayerService(
    private val playerDao: PlayerDao,
    private val gameDao: GameDao,
    private val userDao: UserDao,
    private val boardDao: BoardDao,
) {

    fun addPlayer(gameId: Uuid, userId: Uuid?): Result<Player> {
        val game =
            gameDao.findById(gameId)
                ?: return Result.failure(GameNotFoundException("Game $gameId does not exist."))

        if (userId != null) {
            if (userDao.findById(userId) == null) {
                return Result.failure(InvalidPlayerException("User $userId does not exist."))
            }

            val alreadySeated = playerDao.findByGameId(gameId).any { it.userId?.value == userId }
            if (alreadySeated) {
                return Result.failure(
                    InvalidPlayerException("User $userId is already a player in game $gameId.")
                )
            }
        }

        // A new player starts with however much gold their game's board says every player
        // starts with. The board is guaranteed to exist by a FK from games -> boards.
        val startingGold =
            requireNotNull(boardDao.findStartingGold(game.boardId.value)) {
                "Board ${game.boardId.value} for game $gameId no longer exists."
            }

        return Result.success(playerDao.create(gameId, userId, startingGold))
    }

    /**
     * Returns null if [gameId] doesn't refer to a real game, an empty list if it does but has no
     * players yet.
     */
    fun getPlayers(gameId: Uuid): List<Player>? {
        if (gameDao.findById(gameId) == null) return null

        return playerDao.findByGameId(gameId)
    }
}
