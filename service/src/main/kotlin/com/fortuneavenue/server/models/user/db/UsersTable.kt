package com.fortuneavenue.server.models.user.db

import org.jetbrains.exposed.v1.core.dao.id.UuidTable

object UsersTable : UuidTable("users") {
	val username = varchar("username", 255).uniqueIndex()
}
