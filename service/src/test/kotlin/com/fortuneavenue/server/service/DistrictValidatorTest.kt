package com.fortuneavenue.server.service

import com.fortuneavenue.server.models.board.db.SpaceType
import com.fortuneavenue.server.models.board.rest.CreateBoardPathRequest
import com.fortuneavenue.server.models.board.rest.CreateBoardRequest
import com.fortuneavenue.server.models.board.rest.CreateBoardSpaceRequest
import com.fortuneavenue.server.models.board.rest.CreateDistrictRequest
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DistrictValidatorTest {

    private fun request(
        spaces: List<CreateBoardSpaceRequest> = listOf(CreateBoardSpaceRequest(SpaceType.BASIC)),
        districts: List<CreateDistrictRequest> = emptyList(),
    ) =
        CreateBoardRequest(
            name = "board",
            spaces = spaces,
            paths = listOf(CreateBoardPathRequest(0, 0)),
            startSpaceIndex = 0,
            startingGold = 1000,
            baseSalary = 200,
            promotionBonus = 50,
            districts = districts,
        )

    @Test
    fun `no districts and no district references produce no errors`() {
        assertThat(DistrictValidator.validate(request())).isEmpty()
    }

    @Test
    fun `a valid district and a space referencing it produce no errors`() {
        val req =
            request(
                spaces = listOf(CreateBoardSpaceRequest(SpaceType.BASIC, districtIndex = 0)),
                districts = listOf(CreateDistrictRequest("Red", "FF0000", BigDecimal("0.5000"))),
            )

        assertThat(DistrictValidator.validate(req)).isEmpty()
    }

    @Test
    fun `a district with a malformed colorHex is rejected`() {
        val req =
            request(
                districts =
                    listOf(CreateDistrictRequest("Red", "not-a-color", BigDecimal("0.5000")))
            )

        val errors = DistrictValidator.validate(req)

        assertThat(errors).hasSize(1)
        assertThat(errors.single()).contains("index 0").contains("colorHex")
    }

    @Test
    fun `a colorHex shorter or longer than 6 characters is rejected`() {
        assertThat(
                DistrictValidator.validate(
                    request(
                        districts =
                            listOf(CreateDistrictRequest("Red", "FF00", BigDecimal("0.5000")))
                    )
                )
            )
            .isNotEmpty()
        assertThat(
                DistrictValidator.validate(
                    request(
                        districts =
                            listOf(CreateDistrictRequest("Red", "FF00000", BigDecimal("0.5000")))
                    )
                )
            )
            .isNotEmpty()
    }

    @Test
    fun `a lowercase colorHex is accepted`() {
        val req =
            request(
                districts = listOf(CreateDistrictRequest("Red", "ff00aa", BigDecimal("0.5000")))
            )

        assertThat(DistrictValidator.validate(req)).isEmpty()
    }

    @Test
    fun `a space referencing an out-of-range districtIndex is rejected`() {
        val req =
            request(
                spaces = listOf(CreateBoardSpaceRequest(SpaceType.BASIC, districtIndex = 3)),
                districts = listOf(CreateDistrictRequest("Red", "FF0000", BigDecimal("0.5000"))),
            )

        val errors = DistrictValidator.validate(req)

        assertThat(errors).hasSize(1)
        assertThat(errors.single()).contains("index 0").contains("district index 3")
    }

    @Test
    fun `a minimumStockPercentage just below 1 is accepted`() {
        val req =
            request(
                districts = listOf(CreateDistrictRequest("Red", "FF0000", BigDecimal("0.9999")))
            )

        assertThat(DistrictValidator.validate(req)).isEmpty()
    }

    @Test
    fun `a zero or negative minimumStockPercentage is rejected`() {
        assertThat(
                DistrictValidator.validate(
                    request(
                        districts =
                            listOf(CreateDistrictRequest("Red", "FF0000", BigDecimal("0.0000")))
                    )
                )
            )
            .isNotEmpty()
        assertThat(
                DistrictValidator.validate(
                    request(
                        districts =
                            listOf(CreateDistrictRequest("Red", "FF0000", BigDecimal("-0.5000")))
                    )
                )
            )
            .isNotEmpty()
    }

    @Test
    fun `a minimumStockPercentage of exactly 1, or greater than 1, is rejected`() {
        val exactlyOne =
            request(
                districts = listOf(CreateDistrictRequest("Red", "FF0000", BigDecimal("1.0000")))
            )
        val exactlyOneErrors = DistrictValidator.validate(exactlyOne)
        assertThat(exactlyOneErrors).hasSize(1)
        assertThat(exactlyOneErrors.single()).contains("index 0").contains("minimumStockPercentage")

        val greaterThanOne =
            request(
                districts = listOf(CreateDistrictRequest("Red", "FF0000", BigDecimal("1.0001")))
            )
        val greaterThanOneErrors = DistrictValidator.validate(greaterThanOne)
        assertThat(greaterThanOneErrors).hasSize(1)
        assertThat(greaterThanOneErrors.single())
            .contains("index 0")
            .contains("minimumStockPercentage")
    }

    @Test
    fun `a minimumStockPercentage without exactly 4 digits is rejected`() {
        val req =
            request(districts = listOf(CreateDistrictRequest("Red", "FF0000", BigDecimal("0.5"))))

        val errors = DistrictValidator.validate(req)

        assertThat(errors).hasSize(1)
        assertThat(errors.single()).contains("index 0").contains("minimumStockPercentage")
    }
}
