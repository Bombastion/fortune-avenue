package com.fortuneavenue.server

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import javax.sql.DataSource

/**
 * Base for any `@SpringBootTest` that touches the real database. The test
 * Postgres instance is shared across the whole suite and nothing else ever
 * resets it, so without this, a test can see boards/games/players/etc. left
 * behind by whichever other test happened to run before it -- wiping the
 * schema clean after every test (then rebuilding it via migrations) keeps
 * each test isolated regardless of run order.
 *
 * Builds its own throwaway [Flyway] instance against the same [DataSource]
 * Spring already manages, rather than reusing the app's own autoconfigured
 * Flyway bean: that bean has `clean()` disabled by default (as it should,
 * in case this configuration were ever pointed at something real), and
 * there's no reason this test-only teardown needs to share it.
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
