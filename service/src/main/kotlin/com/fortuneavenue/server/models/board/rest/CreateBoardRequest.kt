package com.fortuneavenue.server.models.board.rest

import com.fortuneavenue.server.models.board.db.SpaceType
import java.math.BigDecimal

/**
 * [baseValue] and [basePricePercentage] only apply to SHOP spaces: [baseValue] must be a positive
 * integer, and [basePricePercentage] must be a decimal strictly between 0 and 1 with exactly 4
 * digits (e.g. 0.1234). Both must be omitted for any other [spaceType]. See [ShopSpaceValidator]
 * for the enforcement of these rules.
 */
data class CreateBoardSpaceRequest(
	val spaceType: SpaceType,
	val baseValue: Int? = null,
	val basePricePercentage: BigDecimal? = null,
)

/**
 * [from] and [to] are indices into the request's [CreateBoardRequest.spaces]
 * list, not real space ids -- those don't exist yet until the board is
 * persisted. [branchOrder] disambiguates which outgoing path this is when a
 * space has more than one (i.e. a fork).
 */
data class CreateBoardPathRequest(
	val from: Int,
	val to: Int,
	val branchOrder: Int = 0,
)

data class CreateBoardRequest(
	val name: String,
	val spaces: List<CreateBoardSpaceRequest>,
	val paths: List<CreateBoardPathRequest>,
	val startSpaceIndex: Int,
)
