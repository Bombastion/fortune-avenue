package com.fortuneavenue.server.models.board.rest

import com.fortuneavenue.server.models.board.db.SpaceType
import java.math.BigDecimal

/**
 * [baseValue] and [basePricePercentage] only apply to SHOP spaces: [baseValue] must be a positive
 * integer, and [basePricePercentage] must be a decimal strictly between 0 and 1 with exactly 4
 * digits (e.g. 0.1234). Both must be omitted for any other [spaceType]. See [ShopSpaceValidator]
 * for the enforcement of these rules.
 *
 * [districtIndex], if present, is an index into [CreateBoardRequest.districts] -- not a real
 * district id, since those don't exist yet until the board is persisted (same idea as
 * [CreateBoardPathRequest.from]/[CreateBoardPathRequest.to]). Every space's district is optional.
 */
data class CreateBoardSpaceRequest(
    val spaceType: SpaceType,
    val baseValue: Int? = null,
    val basePricePercentage: BigDecimal? = null,
    val districtIndex: Int? = null,
)

/**
 * [from] and [to] are indices into the request's [CreateBoardRequest.spaces] list, not real space
 * ids -- those don't exist yet until the board is persisted. [branchOrder] disambiguates which
 * outgoing path this is when a space has more than one (i.e. a fork).
 */
data class CreateBoardPathRequest(
    val from: Int,
    val to: Int,
    val branchOrder: Int = 0,
)

/**
 * [ownedShopCount] is the count of shops a player currently owns in the district (2, 3, 4, ... --
 * never 1, since owning just one has nothing to boost off of yet; that level uses an implied
 * baseline multiplier of 1.0000 instead). [priceMultiplier] scales a shop's dynamically-computed
 * toll price at this level (see GameSimulationService.tollAmount); [maxCapitalMultiplier] scales
 * its baseValue to produce the ceiling on how much capital it can hold (see
 * GameSimulationService.recalculateMaxCaps). Both must be a decimal greater than 1 with exactly 4
 * digits -- multipliers, not additive percentages, with no fixed upper bound.
 */
data class CreateDistrictProgressionRequest(
    val ownedShopCount: Int,
    val priceMultiplier: BigDecimal,
    val maxCapitalMultiplier: BigDecimal,
)

/**
 * [colorHex] must be exactly 6 hex characters (0-9, A-F), e.g. "FF00AA". See [DistrictValidator].
 *
 * [minimumStockPercentage] is the floor, as a fraction of the average value of the district's SHOP
 * spaces, that the district's stock can trade at once a game starts -- a positive decimal strictly
 * between 0 and 1 with exactly 4 digits (e.g. 0.5000 means the stock can never trade below half the
 * district's average shop value). Copied onto game_district_information when a game starts (see
 * [com.fortuneavenue.server.dao.GameDistrictInformationDao.seedForGame]). See [DistrictValidator].
 *
 * [progressions] defines how a shop's toll price and investable capacity scale as a player
 * accumulates more shops in this district: a district with at least 2 spaces (see
 * [CreateBoardRequest.spaces]'s districtIndex) must define exactly one entry for every
 * ownedShopCount from 2 up to that district's total space count; a district with fewer than 2
 * spaces must define none. See [DistrictProgressionValidator].
 */
data class CreateDistrictRequest(
    val name: String,
    val colorHex: String,
    val minimumStockPercentage: BigDecimal,
    val progressions: List<CreateDistrictProgressionRequest> = emptyList(),
)

/**
 * [startingGold] must be a positive integer -- see [BoardService]. Every player in a game on this
 * board starts with this much.
 *
 * [baseSalary] and [promotionBonus] are the B and P terms of the BANK promotion payout formula (see
 * [com.fortuneavenue.server.service.GameSimulationService]): a player crossing/landing on a BANK
 * space while holding all 4 suits is paid baseSalary + (promotionBonus * however many times they've
 * already collected the promotion this game) + the value of every shop they own. [baseSalary] must
 * be a positive integer; [promotionBonus] must be zero or a positive integer -- see [BoardService].
 */
data class CreateBoardRequest(
    val name: String,
    val spaces: List<CreateBoardSpaceRequest>,
    val paths: List<CreateBoardPathRequest>,
    val startSpaceIndex: Int,
    val startingGold: Int,
    val baseSalary: Int,
    val promotionBonus: Int,
    val districts: List<CreateDistrictRequest> = emptyList(),
)
