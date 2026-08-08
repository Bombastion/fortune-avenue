package com.fortuneavenue.server.models.board.rest

import com.fortuneavenue.server.models.board.db.BoardGraph
import com.fortuneavenue.server.models.board.db.SpaceType

data class BoardSpaceResponse(
	val id: String,
	val spaceType: SpaceType,
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

fun BoardGraph.toResponse(): BoardResponse = BoardResponse(
	id = board.id.value.toString(),
	name = board.name,
	startSpaceId = requireNotNull(board.startSpaceId) {
		"Board ${board.id.value} has no start space set."
	}.toString(),
	spaces = spaces.map { BoardSpaceResponse(id = it.id.value.toString(), spaceType = it.spaceType) },
	paths = paths.map {
		BoardPathResponse(
			from = it.fromSpaceId.value.toString(),
			to = it.toSpaceId.value.toString(),
			branchOrder = it.branchOrder,
		)
	},
)
