package com.fortuneavenue.server.service

import com.fortuneavenue.server.models.board.db.SpaceType
import com.fortuneavenue.server.models.board.rest.CreateBoardSpaceRequest
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ShopSpaceValidatorTest {

    private fun shopSpace(
        baseValue: Int? = 100,
        basePricePercentage: BigDecimal? = BigDecimal("0.1000"),
    ) =
        CreateBoardSpaceRequest(
            spaceType = SpaceType.SHOP,
            baseValue = baseValue,
            basePricePercentage = basePricePercentage,
        )

    private fun basicSpace(baseValue: Int? = null, basePricePercentage: BigDecimal? = null) =
        CreateBoardSpaceRequest(
            spaceType = SpaceType.BASIC,
            baseValue = baseValue,
            basePricePercentage = basePricePercentage,
        )

    @Test
    fun `a valid SHOP space produces no errors`() {
        assertThat(ShopSpaceValidator.validate(listOf(shopSpace()))).isEmpty()
    }

    @Test
    fun `a BASIC space with no shop fields produces no errors`() {
        assertThat(ShopSpaceValidator.validate(listOf(basicSpace()))).isEmpty()
    }

    @Test
    fun `a SHOP space missing baseValue is rejected`() {
        val errors = ShopSpaceValidator.validate(listOf(shopSpace(baseValue = null)))

        assertThat(errors).hasSize(1)
        assertThat(errors.single()).contains("index 0").contains("baseValue")
    }

    @Test
    fun `a SHOP space with a zero or negative baseValue is rejected`() {
        assertThat(ShopSpaceValidator.validate(listOf(shopSpace(baseValue = 0)))).isNotEmpty()
        assertThat(ShopSpaceValidator.validate(listOf(shopSpace(baseValue = -5)))).isNotEmpty()
    }

    @Test
    fun `a SHOP space missing basePricePercentage is rejected`() {
        val errors = ShopSpaceValidator.validate(listOf(shopSpace(basePricePercentage = null)))

        assertThat(errors).hasSize(1)
        assertThat(errors.single()).contains("basePricePercentage")
    }

    @Test
    fun `a SHOP space with basePricePercentage outside (0, 1) is rejected`() {
        assertThat(
                ShopSpaceValidator.validate(
                    listOf(shopSpace(basePricePercentage = BigDecimal("0.0000")))
                )
            )
            .isNotEmpty()
        assertThat(
                ShopSpaceValidator.validate(
                    listOf(shopSpace(basePricePercentage = BigDecimal("1.0000")))
                )
            )
            .isNotEmpty()
    }

    @Test
    fun `a SHOP space with basePricePercentage that does not have exactly 4 digits is rejected`() {
        assertThat(
                ShopSpaceValidator.validate(
                    listOf(shopSpace(basePricePercentage = BigDecimal("0.1")))
                )
            )
            .isNotEmpty()
        assertThat(
                ShopSpaceValidator.validate(
                    listOf(shopSpace(basePricePercentage = BigDecimal("0.12345")))
                )
            )
            .isNotEmpty()
    }

    @Test
    fun `a non-SHOP space that includes baseValue is rejected`() {
        val errors = ShopSpaceValidator.validate(listOf(basicSpace(baseValue = 100)))

        assertThat(errors).hasSize(1)
        assertThat(errors.single()).contains("index 0").contains("baseValue")
    }

    @Test
    fun `a non-SHOP space that includes basePricePercentage is rejected`() {
        val errors =
            ShopSpaceValidator.validate(
                listOf(basicSpace(basePricePercentage = BigDecimal("0.1000")))
            )

        assertThat(errors).hasSize(1)
        assertThat(errors.single()).contains("basePricePercentage")
    }

    @Test
    fun `errors reference the index of the offending space`() {
        val errors = ShopSpaceValidator.validate(listOf(shopSpace(), shopSpace(baseValue = null)))

        assertThat(errors).hasSize(1)
        assertThat(errors.single()).contains("index 1")
    }
}
