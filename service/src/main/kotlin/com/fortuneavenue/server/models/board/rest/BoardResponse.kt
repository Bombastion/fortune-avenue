package com.fortuneavenue.server.models.board.rest

import com.fortuneavenue.server.models.board.db.BoardGraph
import com.fortuneavenue.server.models.board.db.SpaceType
import java.math.BigDecimal

data class BoardSpaceResponse(
	val id: String,
	val spaceType: SpaceType,
	val baseValue: Int? = null,
	val basePricePercentage: BigDecimal? = null,
)

data class BoardPathResponse(
	val from: String,
	val to: String,
	val branchOrder: Int,
)

data class BoardResponse(
	val id: String,
	val name: String,
	val startSpaceId: String,
	val spaces: List<BoardSpaceResponse>,
	val paths: List<BoardPathResponse>,
)

fun BoardGraph.toResponse(): BoardResponse {
	val shopInformationBySpaceId = shopInformation.associateBy { it.spaceId.value }

	return BoardResponse(
		id = board.id.value.toString(),
		name = board.name,
		startSpaceId = requireNotNull(board.startSpaceId) {
			"Board ${board.id.value} has no start space set."
		}.toString(),
		spaces = spaces.map { space ->
			val shop = shopInformationBySpaceId[space.id.value]
			BoardSpaceResponse(
				id = space.id.value.toString(),
				spaceType = space.spaceType,
				baseValue = shop?.baseValue,
				basePricePercentage = shop?.basePricePercentage,
			)
		},
		paths = paths.map {
			BoardPathResponse(
				from = it.fromSpaceId.value.toString(),
				to = it.toSpaceId.value.toString(),
				branchOrder = it.branchOrder,
			)
		},
	)
}
