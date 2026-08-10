package com.fortuneavenue.server

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import javax.sql.DataSource

/**
 * Base for any `@SpringBootTest` that touches the real database.
 * Builds its own throwaway [Flyway] instance and runs migrations
 * on the newly clean DB.
 */
abstract class DatabaseTest {

	@Autowired
	private lateinit var dataSource: DataSource

	@AfterEach
	fun cleanDatabase() {
		val flyway = Flyway.configure()
			.dataSource(dataSource)
			.cleanDisabled(false)
			.load()

		flyway.clean()
		flyway.migrate()
	}
}
