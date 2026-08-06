package com.fortuneavenue.server.models.user

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import kotlin.uuid.Uuid

class User(id: EntityID<Uuid>) : UuidEntity(id) {
	companion object : UuidEntityClass<User>(UsersTable)

	var username by UsersTable.username
}
