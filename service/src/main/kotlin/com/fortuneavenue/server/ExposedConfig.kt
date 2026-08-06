package com.fortuneavenue.server

import org.jetbrains.exposed.v1.jdbc.Database
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

/**
 * Points Exposed at the same pooled DataSource Spring already manages
 * (HikariCP, configured via spring.datasource.*), rather than having it
 * open its own separate connection.
 */
@Configuration
class ExposedConfig {

	@Bean
	fun exposedDatabase(dataSource: DataSource): Database = Database.connect(dataSource)
}
