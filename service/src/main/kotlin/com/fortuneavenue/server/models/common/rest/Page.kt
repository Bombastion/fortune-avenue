package com.fortuneavenue.server.models.common.rest

enum class SortDirection {
	ASC,
	DESC,
}

/**
 * A single page of [items], plus enough about how it was fetched to page
 * further -- the [page]/[pageSize]/[direction] that were requested, and
 * [totalPages] at that [pageSize] so a client knows when it's reached the
 * end. Meant to be the return shape for any paginated list endpoint, not
 * just boards -- only [T] (and how it's produced) should ever need to
 * change per endpoint.
 */
data class Page<T>(
	val items: List<T>,
	val page: Int,
	val pageSize: Int,
	val direction: SortDirection,
	val totalPages: Int,
) {
	companion object {
		/** Builds a [Page], computing [Page.totalPages] from [totalItems] at [pageSize]. */
		fun <T> of(items: List<T>, page: Int, pageSize: Int, direction: SortDirection, totalItems: Long): Page<T> {
			val totalPages = if (pageSize < 1) 0 else ((totalItems + pageSize - 1) / pageSize).toInt()
			return Page(items = items, page = page, pageSize = pageSize, direction = direction, totalPages = totalPages)
		}
	}
}

/** Maps every item in a [Page] to a new type -- e.g. a domain object to its REST response DTO -- keeping the pagination metadata as-is. */
fun <T, R> Page<T>.map(transform: (T) -> R): Page<R> = Page(
	items = items.map(transform),
	page = page,
	pageSize = pageSize,
	direction = direction,
	totalPages = totalPages,
)
