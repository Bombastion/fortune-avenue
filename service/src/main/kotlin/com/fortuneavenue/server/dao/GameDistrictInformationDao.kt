package com.fortuneavenue.server.dao

import com.fortuneavenue.server.models.board.db.BoardGraph
import com.fortuneavenue.server.models.board.db.DistrictsTable
import com.fortuneavenue.server.models.board.db.GameDistrictInformation
import com.fortuneavenue.server.models.board.db.GameDistrictInformationTable
import com.fortuneavenue.server.models.board.db.GameShopInformation
import com.fortuneavenue.server.models.game.db.GamesTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository
import java.math.RoundingMode
import kotlin.uuid.Uuid

// Intermediate scale used only while dividing to compute an average -- rounded away again once
// current_stock_value is derived, so this just needs to be generous enough not to lose precision
// along the way.
private const val AVERAGE_INTERMEDIATE_SCALE = 10

@Repository
class GameDistrictInformationDao {

	/**
	 * Seeds one row per district on [boardGraph] that actually contains at least one SHOP space,
	 * using [seededShops] -- the game's just-seeded [GameShopInformation] rows (see
	 * GameShopInformationDao.seedForGame, which must run first and whose output is passed in
	 * here). A district's current_stock_value is the average currentValue of its shops in
	 * [seededShops] (equal to baseValue this early in the game), multiplied by the district's
	 * minimumStockPercentage and rounded to the nearest whole gold. Districts with no SHOP spaces
	 * are skipped -- there's nothing to average. Called once, when a game actually starts (see
	 * GameSimulationService.markReady).
	 */
	fun seedForGame(gameId: Uuid, boardGraph: BoardGraph, seededShops: List<GameShopInformation>): List<GameDistrictInformation> = transaction {
		val shopsByDistrictId = seededShops.filter { it.districtId != null }.groupBy { it.districtId!!.value }

		boardGraph.districts.mapNotNull { district ->
			val shops = shopsByDistrictId[district.id.value]
			if (shops.isNullOrEmpty()) return@mapNotNull null

			val average = shops.sumOf { it.currentValue }
				.toBigDecimal()
				.divide(shops.size.toBigDecimal(), AVERAGE_INTERMEDIATE_SCALE, RoundingMode.HALF_UP)
			val currentStockValue = (average * district.minimumStockPercentage).setScale(0, RoundingMode.HALF_UP).toInt()

			GameDistrictInformation.new {
				this.gameId = EntityID(gameId, GamesTable)
				districtId = district.id
				boardId = district.boardId
				minimumStockPercentage = district.minimumStockPercentage
				this.currentStockValue = currentStockValue
			}
		}
	}

	fun findByGameAndDistrict(gameId: Uuid, districtId: Uuid): GameDistrictInformation? = transaction {
		GameDistrictInformation.find {
			(GameDistrictInformationTable.gameId eq EntityID(gameId, GamesTable)) and
				(GameDistrictInformationTable.districtId eq EntityID(districtId, DistrictsTable))
		}.firstOrNull()
	}
}
