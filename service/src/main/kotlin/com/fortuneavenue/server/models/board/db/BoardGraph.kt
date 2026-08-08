package com.fortuneavenue.server.models.board.db

/**
 * A fully loaded board: the board row plus all of its spaces and paths.
 * Not itself a table -- just the shape the DAO hands back once everything
 * belonging to a board has been fetched (or just created) together.
 */
data class BoardGraph(
	val board: Board,
	val spaces: List<BoardSpace>,
	val paths: List<BoardPath>,
)
