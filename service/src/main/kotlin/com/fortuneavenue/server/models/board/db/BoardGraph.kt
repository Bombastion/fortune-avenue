package com.fortuneavenue.server.models.board.db

/**
 * A fully loaded board
 */
data class BoardGraph(
	val board: Board,
	val spaces: List<BoardSpace>,
	val paths: List<BoardPath>,
	val shopInformation: List<ShopInformation> = emptyList(),
)
