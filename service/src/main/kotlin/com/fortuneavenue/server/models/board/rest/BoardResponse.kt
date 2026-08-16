package com.fortuneavenue.server.models.board.rest

import com.fortuneavenue.server.models.board.db.BoardGraph
import com.fortuneavenue.server.models.board.db.SpaceType
import java.math.BigDecimal

data class BoardSpaceResponse(
	val id: String,
	val spaceType: SpaceType,
	val baseValue: Int? = null,
	val basePricePercentage: BigDecimal? = null,
	val districtId: String? = null,
)

data class BoardPathResponse(
	val from: String,
	val to: String,
	val branchOrder: Int,
)

data class DistrictProgressionResponse(
	val ownedShopCount: Int,
	val existingShopBoostPercentage: BigDecimal,
	val newShopBoostPercentage: BigDecimal,
)

data class DistrictResponse(
	val id: String,
	val name: String,
	val colorHex: String,
	val minimumStockPercentage: BigDecimal,
	val progressions: List<DistrictProgressionResponse> = emptyList(),
)

data class BoardResponse(
	val id: String,
	val name: String,
	val startSpaceId: String,
	val startingGold: Int,
	val spaces: List<BoardSpaceResponse>,
	val paths: List<BoardPathResponse>,
	val districts: List<DistrictResponse> = emptyList(),
)

fun BoardGraph.toResponse(): BoardResponse {
	val shopInformationBySpaceId = shopInformation.associateBy { it.spaceId.value }
	val progressionsByDistrictId = districtProgressions.groupBy { it.districtId.value }

	return BoardResponse(
		id = board.id.value.toString(),
		name = board.name,
		startSpaceId = requireNotNull(board.startSpaceId) {
			"Board ${board.id.value} has no start space set."
		}.toString(),
		startingGold = board.startingGold,
		spaces = spaces.map { space ->
			val shop = shopInformationBySpaceId[space.id.value]
			BoardSpaceResponse(
				id = space.id.value.toString(),
				spaceType = space.spaceType,
				baseValue = shop?.baseValue,
				basePricePercentage = shop?.basePricePercentage,
				districtId = space.districtId?.value?.toString(),
			)
		},
		paths = paths.map {
			BoardPathResponse(
				from = it.fromSpaceId.value.toString(),
				to = it.toSpaceId.value.toString(),
				branchOrder = it.branchOrder,
			)
		},
		districts = districts.map { district ->
			DistrictResponse(
				id = district.id.value.toString(),
				name = district.name,
				colorHex = district.colorHex,
				minimumStockPercentage = district.minimumStockPercentage,
				progressions = progressionsByDistrictId[district.id.value].orEmpty()
					.sortedBy { it.ownedShopCount }
					.map { DistrictProgressionResponse(it.ownedShopCount, it.existingShopBoostPercentage, it.newShopBoostPercentage) },
			)
		},
	)
}
