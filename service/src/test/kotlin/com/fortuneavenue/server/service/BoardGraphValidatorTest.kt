package com.fortuneavenue.server.service

import com.fortuneavenue.server.service.BoardGraphValidator.Edge
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BoardGraphValidatorTest {

	@Test
	fun `a simple loop back to start is valid`() {
		// 0 -> 1 -> 2 -> 0
		val edges = listOf(Edge(0, 1), Edge(1, 2), Edge(2, 0))

		val errors = BoardGraphValidator.validate(spaceCount = 3, edges = edges, start = 0)

		assertThat(errors).isEmpty()
	}

	@Test
	fun `a branch that forks and rejoins before returning to start is valid`() {
		// 0 -> 1 -> 3 -> 0
		// 0 -> 2 -> 3
		val edges = listOf(Edge(0, 1), Edge(0, 2), Edge(1, 3), Edge(2, 3), Edge(3, 0))

		val errors = BoardGraphValidator.validate(spaceCount = 4, edges = edges, start = 0)

		assertThat(errors).isEmpty()
	}

	@Test
	fun `a space with no path from start is invalid`() {
		// 0 <-> 1 is the loop reachable from start.
		// 2 -> 3 -> 0 can get back to start, but nothing ever leads *to* 2 or
		// 3 from start, so they're unreachable despite being able to return.
		// This isolates the "unreachable" check from the "can't return"
		// check, since an isolated node with no edges at all would fail
		// both simultaneously and not prove this check works on its own.
		val edges = listOf(Edge(0, 1), Edge(1, 0), Edge(2, 3), Edge(3, 0))

		val errors = BoardGraphValidator.validate(spaceCount = 4, edges = edges, start = 0)

		assertThat(errors).hasSize(1)
		assertThat(errors.single()).contains("not reachable")
	}

	@Test
	fun `a dead-end branch that never returns to start is invalid`() {
		// 0 -> 1 -> 2, and 2 has no way back to 0
		val edges = listOf(Edge(0, 1), Edge(1, 2))

		val errors = BoardGraphValidator.validate(spaceCount = 3, edges = edges, start = 0)

		assertThat(errors).hasSize(1)
		assertThat(errors.single()).contains("can never make it back")
	}

	@Test
	fun `a branch reachable from start that loops on itself without touching start is invalid`() {
		// 0 -> 1 -> 0 (the main loop, fine on its own)
		// 0 -> 2 -> 3 -> 2 (reachable from start, but 2 and 3 only ever cycle
		// with each other -- forward reachability alone would call this
		// board valid, since every space *is* reachable from start; only
		// checking the reverse direction catches that 2 and 3 can't get back)
		val edges = listOf(Edge(0, 1), Edge(1, 0), Edge(0, 2), Edge(2, 3), Edge(3, 2))

		val errors = BoardGraphValidator.validate(spaceCount = 4, edges = edges, start = 0)

		assertThat(errors).hasSize(1)
		assertThat(errors.single()).contains("can never make it back")
		assertThat(errors.single()).contains("2", "3")
	}

	@Test
	fun `an out-of-range start index is invalid`() {
		val errors = BoardGraphValidator.validate(spaceCount = 2, edges = emptyList(), start = 5)

		assertThat(errors).hasSize(1)
		assertThat(errors.single()).contains("out of range")
	}

	@Test
	fun `an edge referencing an out-of-range space index is invalid`() {
		val errors = BoardGraphValidator.validate(
			spaceCount = 2,
			edges = listOf(Edge(0, 1), Edge(1, 9)),
			start = 0,
		)

		assertThat(errors).hasSize(1)
		assertThat(errors.single()).contains("outside the valid range")
	}

	@Test
	fun `a board with no spaces is invalid`() {
		val errors = BoardGraphValidator.validate(spaceCount = 0, edges = emptyList(), start = 0)

		assertThat(errors).hasSize(1)
		assertThat(errors.single()).contains("at least one space")
	}
}
