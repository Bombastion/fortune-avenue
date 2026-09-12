package com.fortuneavenue.server.dao

import com.fortuneavenue.server.models.board.db.BoardGraph
import com.fortuneavenue.server.models.board.db.DistrictsTable
import com.fortuneavenue.server.models.board.db.GameDistrictInformation
import com.fortuneavenue.server.models.board.db.GameDistrictInformationTable
import com.fortuneavenue.server.models.board.db.GameShopInformation
import com.fortuneavenue.server.models.game.db.GamesTable
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository

// The real game (per FortuneStreetModding's own board editor's "Tools > Stock Prices" preview,
// Editor/MainWindow.xaml.cs, and their district simulator at
// fortunestreetmodding.github.io/simulator, src/pages/simulator.js) computes every district's
// base stock value with one fixed 16.16 fixed-point multiplier applied identically everywhere --
// not a per-district configurable floor. 0x0B00 / 0x10000 is that multiplier, roughly 4.3%. See
// [computeCurrentStockValue].
private const val STOCK_VALUE_NUMERATOR = 0x0B00
private const val STOCK_VALUE_DENOMINATOR = 0x10000

@Repository
class GameDistrictInformationDao {

    /**
     * Seeds one row per district on [boardGraph] that actually contains at least one SHOP space,
     * using [seededShops] -- the game's just-seeded [GameShopInformation] rows (see
     * GameShopInformationDao.seedForGame, which must run first and whose output is passed in here).
     * A district's current_stock_value is [computeCurrentStockValue] of its shops in [seededShops]
     * (equal to baseValue this early in the game). Districts with no SHOP spaces are skipped --
     * there's nothing to average. Called once, when a game actually starts (see
     * GameSimulationService.markReady).
     */
    fun seedForGame(
        gameId: Uuid,
        boardGraph: BoardGraph,
        seededShops: List<GameShopInformation>,
    ): List<GameDistrictInformation> = transaction {
        val shopsByDistrictId =
            seededShops.filter { it.districtId != null }.groupBy { it.districtId!!.value }

        boardGraph.districts.mapNotNull { district ->
            val shops = shopsByDistrictId[district.id.value]
            if (shops.isNullOrEmpty()) return@mapNotNull null

            GameDistrictInformation.new {
                this.gameId = EntityID(gameId, GamesTable)
                districtId = district.id
                boardId = district.boardId
                currentStockValue = computeCurrentStockValue(shops)
            }
        }
    }

    /**
     * Recomputes and persists [districtId]'s current_stock_value in [gameId] from [shops] -- every
     * shop in the district (see
     * [com.fortuneavenue.server.dao.GameShopInformationDao.findByGameAndDistrict]), not just the
     * ones whose value just changed: a purchase's district value progression only boosts the shops
     * the buyer owns there, but current_stock_value averages every shop in the district regardless
     * of owner. A no-op (returns null) if [districtId] has no seeded row
     */
    fun recalculateCurrentStockValue(
        gameId: Uuid,
        districtId: EntityID<Uuid>,
        shops: List<GameShopInformation>,
    ): GameDistrictInformation? = transaction {
        if (shops.isEmpty()) return@transaction null

        val info =
            GameDistrictInformation.find {
                    (GameDistrictInformationTable.gameId eq EntityID(gameId, GamesTable)) and
                        (GameDistrictInformationTable.districtId eq districtId)
                }
                .firstOrNull() ?: return@transaction null

        info.apply { currentStockValue = computeCurrentStockValue(shops) }
    }

    /**
     * Persists [currentStockValue] as [id]'s current_stock_value, e.g. after GameSimulationService
     * works out a post-trade price fluctuation.
     */
    fun setCurrentStockValue(id: Uuid, currentStockValue: Int): GameDistrictInformation? =
        transaction {
            GameDistrictInformation.findById(id)?.apply {
                this.currentStockValue = currentStockValue
            }
        }

    /**
     * The seeded row itself, e.g. to look up a district's current_stock_value from a PlayerStock's
     * gameDistrictInformationId (see GameSimulationService.netWorth). Null if [id] doesn't exist.
     */
    fun findById(id: Uuid): GameDistrictInformation? = transaction {
        GameDistrictInformation.findById(id)
    }

    fun findByGameAndDistrict(gameId: Uuid, districtId: Uuid): GameDistrictInformation? =
        transaction {
            GameDistrictInformation.find {
                    (GameDistrictInformationTable.gameId eq EntityID(gameId, GamesTable)) and
                        (GameDistrictInformationTable.districtId eq
                            EntityID(districtId, DistrictsTable))
                }
                .firstOrNull()
        }

    /**
     * Every district in [gameId] that got a seeded row -- i.e. every district containing at least
     * one SHOP space (see [seedForGame])
     */
    fun findAllByGame(gameId: Uuid): List<GameDistrictInformation> = transaction {
        GameDistrictInformation.find {
                GameDistrictInformationTable.gameId eq EntityID(gameId, GamesTable)
            }
            .toList()
    }

    /**
     * The average currentValue of [shops] (floored, integer division), scaled by
     * [STOCK_VALUE_NUMERATOR] / [STOCK_VALUE_DENOMINATOR] and floored again -- see the constants'
     * own doc for where this fixed-point multiplier comes from. Mirrors
     * GameSimulationService.averageStockValue, kept as a separate copy here rather than shared so
     * that DAO stays limited to querying and persisting, not deciding prices.
     */
    private fun computeCurrentStockValue(shops: List<GameShopInformation>): Int {
        val stockBase = shops.sumOf { it.currentValue } / shops.size
        return (stockBase * STOCK_VALUE_NUMERATOR) / STOCK_VALUE_DENOMINATOR
    }
}
