package com.fortuneavenue.server

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class HealthControllerTest {

	@Autowired
	lateinit var restTemplate: TestRestTemplate

	@Test
	fun `health endpoint returns 200 with ok status`() {
		val response = restTemplate.getForEntity("/health", Map::class.java)

		assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
		assertThat(response.body).isEqualTo(mapOf("status" to "ok"))
	}
}
