package com.fortuneavenue.server.dao

import com.fortuneavenue.server.models.board.db.BoardGraph
import com.fortuneavenue.server.models.board.db.BoardSpacesTable
import com.fortuneavenue.server.models.board.db.GameShopInformation
import com.fortuneavenue.server.models.board.db.GameShopInformationTable
import com.fortuneavenue.server.models.game.db.GamesTable
import com.fortuneavenue.server.models.player.db.PlayersTable
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository

@Repository
class GameShopInformationDao {

    /**
     * Seeds one row per SHOP space on [boardGraph], copying board_id/space_id/base_value/
     * base_price_percentage/district_id from the board's (reusable) shop_information/board_spaces
     * rows -- current_value starts at base_value, current_investment at 0, and owner_id is null
     * until a player buys in. Called once, when a game actually starts (see
     * GameSimulationService.markReady).
     *
     * max_cap starts at 0 -- an unowned shop has no investable capacity, same as an owned one
     * that's already at its ceiling. It's given a real value (baseValue times the district's
     * progression, minus currentValue) the moment a player buys in -- see
     * GameSimulationService.recalculateMaxCaps.
     */
    fun seedForGame(gameId: Uuid, boardGraph: BoardGraph): List<GameShopInformation> = transaction {
        val spacesById = boardGraph.spaces.associateBy { it.id.value }

        boardGraph.shopInformation.map { shopInfo ->
            GameShopInformation.new {
                this.gameId = EntityID(gameId, GamesTable)
                shopInformationId = shopInfo.id
                boardId = shopInfo.boardId
                spaceId = shopInfo.spaceId
                baseValue = shopInfo.baseValue
                basePricePercentage = shopInfo.basePricePercentage
                districtId = spacesById[shopInfo.spaceId.value]?.districtId
                currentValue = shopInfo.baseValue
                currentInvestment = 0
                maxCap = 0
            }
        }
    }

    /**
     * Every shop in [gameId], regardless of owner (or lack of one) -- see
     * GameSimulationService.getSnapshot, which uses this to give a reconnecting client every shop's
     * current value, not just the ones it happens to own.
     */
    fun findAllByGame(gameId: Uuid): List<GameShopInformation> = transaction {
        GameShopInformation.find { GameShopInformationTable.gameId eq EntityID(gameId, GamesTable) }
            .toList()
    }

    fun findByGameAndSpace(gameId: Uuid, spaceId: Uuid): GameShopInformation? = transaction {
        GameShopInformation.find {
                (GameShopInformationTable.gameId eq EntityID(gameId, GamesTable)) and
                    (GameShopInformationTable.spaceId eq EntityID(spaceId, BoardSpacesTable))
            }
            .firstOrNull()
    }

    fun findOwnedByPlayerInDistrict(
        gameId: Uuid,
        playerId: Uuid,
        districtId: EntityID<Uuid>,
    ): List<GameShopInformation> = transaction {
        GameShopInformation.find {
                (GameShopInformationTable.gameId eq EntityID(gameId, GamesTable)) and
                    (GameShopInformationTable.ownerId eq EntityID(playerId, PlayersTable)) and
                    (GameShopInformationTable.districtId eq districtId)
            }
            .toList()
    }

    /**
     * Every shop [playerId] owns in [gameId], across every district (or none) -- see
     * GameSimulationService's BANK promotion payout, which sums these shops' currentValue. Unlike
     * [findOwnedByPlayerInDistrict], not scoped to a single district.
     */
    fun findOwnedByPlayer(gameId: Uuid, playerId: Uuid): List<GameShopInformation> = transaction {
        GameShopInformation.find {
                (GameShopInformationTable.gameId eq EntityID(gameId, GamesTable)) and
                    (GameShopInformationTable.ownerId eq EntityID(playerId, PlayersTable))
            }
            .toList()
    }

    /** Every shop in [districtId], regardless of owner (or lack of one) */
    fun findByGameAndDistrict(gameId: Uuid, districtId: EntityID<Uuid>): List<GameShopInformation> =
        transaction {
            GameShopInformation.find {
                    (GameShopInformationTable.gameId eq EntityID(gameId, GamesTable)) and
                        (GameShopInformationTable.districtId eq districtId)
                }
                .toList()
        }

    /**
     * Hands [id] to [playerId] -- but only if it's still unowned, checked and set in the same
     * transaction so this is safe even without the per-game lock GameSimulationService's actions
     * already serialize behind (see GameSimulationService.gameLocks): a shop only ever gets one
     * owner. Returns null (a no-op) if it's already owned by someone -- including [playerId]
     * themselves -- so a caller can tell a real purchase from a race it lost, rather than silently
     * "succeeding" at rebuying something.
     */
    fun setOwner(id: Uuid, playerId: Uuid): GameShopInformation? = transaction {
        GameShopInformation.findById(id)
            ?.takeIf { it.ownerId == null }
            ?.apply { ownerId = EntityID(playerId, PlayersTable) }
    }

    fun setCurrentValue(id: Uuid, currentValue: Int): GameShopInformation? = transaction {
        GameShopInformation.findById(id)?.apply { this.currentValue = currentValue }
    }

    /**
     * Persists [maxCap] as [id]'s max_cap -- see GameSimulationService.recalculateMaxCaps, which
     * works out the new ceiling (baseValue times the district's progression for the owner's
     * current owned_shop_count there) minus currentValue, for every shop that owner holds in a
     * district right after a purchase changes their dominance level there.
     */
    fun setMaxCap(id: Uuid, maxCap: Int): GameShopInformation? = transaction {
        GameShopInformation.findById(id)?.apply { this.maxCap = maxCap }
    }

    /**
     * Applies a player's investment of [amount] gold into shop [id]: raises currentValue and
     * currentInvestment by [amount], and lowers max_cap by that same amount (headroom just spent)
     * -- all three in one transaction, so nothing can read this shop mid-update with only some of
     * them changed. See GameSimulationService.invest, the only caller, which has already validated
     * [amount] against this shop's remaining max_cap and the investing player's own gold before
     * ever reaching here -- this never checks anything itself, in keeping with business logic
     * staying out of DAO classes.
     */
    fun applyInvestment(id: Uuid, amount: Int): GameShopInformation? = transaction {
        GameShopInformation.findById(id)?.apply {
            currentValue += amount
            currentInvestment += amount
            maxCap -= amount
        }
    }
}
