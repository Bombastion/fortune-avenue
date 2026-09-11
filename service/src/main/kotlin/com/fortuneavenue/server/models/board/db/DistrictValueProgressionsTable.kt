package com.fortuneavenue.server.models.board.db

import org.jetbrains.exposed.v1.core.dao.id.UuidTable

// Precision is higher than a plain percentage (e.g. basePricePercentage's) since these are
// multipliers with no fixed upper bound -- see the migration.
private const val MULTIPLIER_PRECISION = 7
private const val MULTIPLIER_SCALE = 4

/**
 * How a shop's toll price and investable capacity scale as a single player accumulates more
 * shops in its district. One row per step -- see the migration for the exact mechanics and the
 * constraints enforced at the DB level. Deliberately per-district (not a shared board-level
 * curve): same-sized districts can still scale differently from each other if board makers
 * desire.
 *
 * Looked up fresh every time (see GameSimulationService's tollAmount/recalculateMaxCaps), keyed
 * by however many shops the shop's *current* owner owns in the district right now -- never
 * compounded or baked permanently into a shop's stored value.
 */
object DistrictValueProgressionsTable : UuidTable("district_value_progressions") {
    val districtId = reference("district_id", DistrictsTable)

    // The count of shops in the district the player currently owns (2, 3, 4, ...). Never 1 --
    // owning a single shop in a district has nothing to boost off of yet, so that level uses an
    // implied baseline multiplier of 1.0000 instead of a row here.
    val ownedShopCount = integer("owned_shop_count")

    // Multiplies a shop's dynamically-computed toll price (see
    // GameSimulationService.tollAmount) whenever its owner currently owns [ownedShopCount] shops
    // in this district. A multiplier, not an additive percentage -- e.g. 1.2500 means tolls are
    // 1.25x what the base formula alone would give.
    val priceMultiplier =
        decimal("price_multiplier", MULTIPLIER_PRECISION, MULTIPLIER_SCALE)

    // Multiplies a shop's baseValue to get the ceiling on how much capital it can hold once its
    // owner currently owns [ownedShopCount] shops in this district -- max_cap (the room left to
    // invest) is that ceiling minus the shop's currentValue (see
    // GameSimulationService.recalculateMaxCaps).
    val maxCapitalMultiplier =
        decimal("max_capital_multiplier", MULTIPLIER_PRECISION, MULTIPLIER_SCALE)

    init {
        uniqueIndex(districtId, ownedShopCount)
    }
}
