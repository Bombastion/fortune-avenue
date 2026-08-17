package com.fortuneavenue.server.service

import com.fortuneavenue.server.models.board.db.SpaceType
import com.fortuneavenue.server.models.board.rest.CreateBoardPathRequest
import com.fortuneavenue.server.models.board.rest.CreateBoardRequest
import com.fortuneavenue.server.models.board.rest.CreateBoardSpaceRequest
import com.fortuneavenue.server.models.board.rest.CreateDistrictProgressionRequest
import com.fortuneavenue.server.models.board.rest.CreateDistrictRequest
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DistrictProgressionValidatorTest {

    private fun progression(
        ownedShopCount: Int,
        existing: String = "0.1000",
        new: String = "0.1500",
    ) = CreateDistrictProgressionRequest(ownedShopCount, BigDecimal(existing), BigDecimal(new))

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
    fun `no districts produce no errors`() {
        assertThat(DistrictProgressionValidator.validate(request())).isEmpty()
    }

    @Test
    fun `a district with fewer than 2 spaces and no progressions produces no errors`() {
        val req =
            request(
                spaces = listOf(CreateBoardSpaceRequest(SpaceType.BASIC, districtIndex = 0)),
                districts = listOf(CreateDistrictRequest("Red", "FF0000", BigDecimal("0.5000"))),
            )

        assertThat(DistrictProgressionValidator.validate(req)).isEmpty()
    }

    @Test
    fun `a district with fewer than 2 spaces and a progression entry is rejected`() {
        val req =
            request(
                spaces = listOf(CreateBoardSpaceRequest(SpaceType.BASIC, districtIndex = 0)),
                districts =
                    listOf(
                        CreateDistrictRequest(
                            "Red",
                            "FF0000",
                            minimumStockPercentage = BigDecimal("0.5000"),
                            progressions = listOf(progression(2)),
                        )
                    ),
            )

        val errors = DistrictProgressionValidator.validate(req)

        assertThat(errors).hasSize(1)
        assertThat(errors.single()).contains("index 0").contains("unexpected [2]")
    }

    @Test
    fun `a district with 2 spaces and one progression entry for ownedShopCount 2 produces no errors`() {
        val req =
            request(
                spaces =
                    listOf(
                        CreateBoardSpaceRequest(SpaceType.BASIC, districtIndex = 0),
                        CreateBoardSpaceRequest(SpaceType.BASIC, districtIndex = 0),
                    ),
                districts =
                    listOf(
                        CreateDistrictRequest(
                            "Red",
                            "FF0000",
                            minimumStockPercentage = BigDecimal("0.5000"),
                            progressions = listOf(progression(2)),
                        )
                    ),
            )

        assertThat(DistrictProgressionValidator.validate(req)).isEmpty()
    }

    @Test
    fun `a district with 3 spaces requires entries for ownedShopCount 2 and 3`() {
        val spaces = List(3) { CreateBoardSpaceRequest(SpaceType.BASIC, districtIndex = 0) }
        val complete =
            request(
                spaces = spaces,
                districts =
                    listOf(
                        CreateDistrictRequest(
                            "Red",
                            "FF0000",
                            minimumStockPercentage = BigDecimal("0.5000"),
                            progressions = listOf(progression(2), progression(3)),
                        )
                    ),
            )
        assertThat(DistrictProgressionValidator.validate(complete)).isEmpty()

        val missingOne =
            request(
                spaces = spaces,
                districts =
                    listOf(
                        CreateDistrictRequest(
                            "Red",
                            "FF0000",
                            minimumStockPercentage = BigDecimal("0.5000"),
                            progressions = listOf(progression(2)),
                        )
                    ),
            )
        val errors = DistrictProgressionValidator.validate(missingOne)

        assertThat(errors).hasSize(1)
        assertThat(errors.single()).contains("index 0").contains("missing [3]")
    }

    @Test
    fun `a district with 2 spaces and no progression entries is rejected`() {
        val req =
            request(
                spaces =
                    listOf(
                        CreateBoardSpaceRequest(SpaceType.BASIC, districtIndex = 0),
                        CreateBoardSpaceRequest(SpaceType.BASIC, districtIndex = 0),
                    ),
                districts = listOf(CreateDistrictRequest("Red", "FF0000", BigDecimal("0.5000"))),
            )

        val errors = DistrictProgressionValidator.validate(req)

        assertThat(errors).hasSize(1)
        assertThat(errors.single()).contains("index 0").contains("missing [2]")
    }

    @Test
    fun `duplicate ownedShopCount entries are rejected`() {
        val req =
            request(
                spaces =
                    listOf(
                        CreateBoardSpaceRequest(SpaceType.BASIC, districtIndex = 0),
                        CreateBoardSpaceRequest(SpaceType.BASIC, districtIndex = 0),
                    ),
                districts =
                    listOf(
                        CreateDistrictRequest(
                            "Red",
                            "FF0000",
                            minimumStockPercentage = BigDecimal("0.5000"),
                            progressions = listOf(progression(2), progression(2)),
                        )
                    ),
            )

        val errors = DistrictProgressionValidator.validate(req)

        assertThat(errors.any { it.contains("duplicate") && it.contains("[2]") }).isTrue()
    }

    @Test
    fun `a zero or negative boost percentage, or one with the wrong scale, is rejected`() {
        val spaces =
            listOf(
                CreateBoardSpaceRequest(SpaceType.BASIC, districtIndex = 0),
                CreateBoardSpaceRequest(SpaceType.BASIC, districtIndex = 0),
            )

        val zero =
            request(
                spaces = spaces,
                districts =
                    listOf(
                        CreateDistrictRequest(
                            "Red",
                            "FF0000",
                            minimumStockPercentage = BigDecimal("0.5000"),
                            progressions = listOf(progression(2, existing = "0.0000")),
                        )
                    ),
            )
        assertThat(DistrictProgressionValidator.validate(zero)).isNotEmpty()

        val negative =
            request(
                spaces = spaces,
                districts =
                    listOf(
                        CreateDistrictRequest(
                            "Red",
                            "FF0000",
                            minimumStockPercentage = BigDecimal("0.5000"),
                            progressions = listOf(progression(2, new = "-0.1000")),
                        )
                    ),
            )
        assertThat(DistrictProgressionValidator.validate(negative)).isNotEmpty()

        val wrongScale =
            request(
                spaces = spaces,
                districts =
                    listOf(
                        CreateDistrictRequest(
                            "Red",
                            "FF0000",
                            minimumStockPercentage = BigDecimal("0.5000"),
                            progressions = listOf(progression(2, existing = "0.1")),
                        )
                    ),
            )
        assertThat(DistrictProgressionValidator.validate(wrongScale)).isNotEmpty()
    }

    @Test
    fun `a boost percentage greater than 1 is accepted -- boosts are multipliers with no upper bound`() {
        val spaces =
            listOf(
                CreateBoardSpaceRequest(SpaceType.BASIC, districtIndex = 0),
                CreateBoardSpaceRequest(SpaceType.BASIC, districtIndex = 0),
            )
        val req =
            request(
                spaces = spaces,
                districts =
                    listOf(
                        CreateDistrictRequest(
                            "Red",
                            "FF0000",
                            minimumStockPercentage = BigDecimal("0.5000"),
                            progressions =
                                listOf(progression(2, existing = "1.5000", new = "2.0000")),
                        )
                    ),
            )

        assertThat(DistrictProgressionValidator.validate(req)).isEmpty()
    }
}
