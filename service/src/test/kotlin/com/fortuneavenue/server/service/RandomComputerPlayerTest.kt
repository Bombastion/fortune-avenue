package com.fortuneavenue.server.service

import com.fortuneavenue.server.models.board.db.GameShopInformation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock

class RandomComputerPlayerTest {

    private val computerPlayer = RandomComputerPlayer()

    private fun mockShop(currentValue: Int): GameShopInformation {
        val shop = mock(GameShopInformation::class.java)
        given(shop.currentValue).willReturn(currentValue)
        return shop
    }

    @Test
    fun `chooseBranch picks one of the given options`() {
        val options = listOf("a", "b", "c")

        assertThat(options).contains(computerPlayer.chooseBranch(options))
    }

    @Test
    fun `shouldBuyShop returns true when currentGold exceeds the shop's price`() {
        val shop = mockShop(currentValue = 100)

        assertThat(computerPlayer.shouldBuyShop(shop, currentGold = 500)).isTrue()
    }

    @Test
    fun `shouldBuyShop returns true when currentGold exactly covers the shop's price`() {
        val shop = mockShop(currentValue = 100)

        assertThat(computerPlayer.shouldBuyShop(shop, currentGold = 100)).isTrue()
    }

    @Test
    fun `shouldBuyShop returns false when currentGold falls short of the shop's price`() {
        val shop = mockShop(currentValue = 100)

        assertThat(computerPlayer.shouldBuyShop(shop, currentGold = 99)).isFalse()
    }

    @Test
    fun `shouldBuyShop returns false when currentGold is already negative`() {
        val shop = mockShop(currentValue = 100)

        assertThat(computerPlayer.shouldBuyShop(shop, currentGold = -50)).isFalse()
    }
}
