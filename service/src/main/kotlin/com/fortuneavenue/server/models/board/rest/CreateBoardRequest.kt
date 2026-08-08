package com.fortuneavenue.server.models.board.rest

import com.fortuneavenue.server.models.board.db.SpaceType

data class CreateBoardSpaceRequest(
	val spaceType: SpaceType,
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
