package com.fortuneavenue.server.service

import com.fortuneavenue.server.models.board.db.SpaceType
import com.fortuneavenue.server.models.board.rest.CreateBoardSpaceRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RequiredSpaceTypesValidatorTest {

	private val completeSet = listOf(
		CreateBoardSpaceRequest(SpaceType.BASIC),
		CreateBoardSpaceRequest(SpaceType.BANK),
		CreateBoardSpaceRequest(SpaceType.HEART),
		CreateBoardSpaceRequest(SpaceType.DIAMOND),
		CreateBoardSpaceRequest(SpaceType.SPADE),
		CreateBoardSpaceRequest(SpaceType.CLUB),
	)

	@Test
	fun `a board with a BANK space and all 4 suits produces no errors`() {
		assertThat(RequiredSpaceTypesValidator.validate(completeSet)).isEmpty()
	}

	@Test
	fun `extra BASIC and SHOP spaces alongside a complete set produce no errors`() {
		val spaces = completeSet + CreateBoardSpaceRequest(SpaceType.SHOP, baseValue = 100, basePricePercentage = null)

		assertThat(RequiredSpaceTypesValidator.validate(spaces)).isEmpty()
	}

	@Test
	fun `a board missing a BANK space is rejected`() {
		val spaces = completeSet.filterNot { it.spaceType == SpaceType.BANK }

		val errors = RequiredSpaceTypesValidator.validate(spaces)

		assertThat(errors).hasSize(1)
		assertThat(errors.single()).contains("BANK")
	}

	@Test
	fun `a board missing one suit is rejected`() {
		val spaces = completeSet.filterNot { it.spaceType == SpaceType.CLUB }

		val errors = RequiredSpaceTypesValidator.validate(spaces)

		assertThat(errors).hasSize(1)
		assertThat(errors.single()).contains("CLUB")
	}

	@Test
	fun `a board missing BANK and every suit produces a single error naming all of them`() {
		val spaces = listOf(CreateBoardSpaceRequest(SpaceType.BASIC))

		val errors = RequiredSpaceTypesValidator.validate(spaces)

		assertThat(errors).hasSize(1)
		assertThat(errors.single()).contains("BANK").contains("HEART").contains("DIAMOND").contains("SPADE").contains("CLUB")
	}

	@Test
	fun `an empty board is rejected`() {
		assertThat(RequiredSpaceTypesValidator.validate(emptyList())).isNotEmpty()
	}

	@Test
	fun `multiple BANK spaces or multiple of the same suit still satisfy the requirement`() {
		val spaces = completeSet + CreateBoardSpaceRequest(SpaceType.BANK) + CreateBoardSpaceRequest(SpaceType.HEART)

		assertThat(RequiredSpaceTypesValidator.validate(spaces)).isEmpty()
	}
}
