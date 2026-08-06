package com.fortuneavenue.server.models.user.rest

import com.fortuneavenue.server.models.user.db.User

/**
 * A dedicated JSON representation of [User], rather than serializing the
 * Exposed DAO entity directly. Entities rely on delegated properties that
 * only resolve inside an active transaction and don't have the shape
 * Jackson expects (no no-arg constructor, id wrapped in EntityID<Uuid>),
 * so serializing them straight from a controller is fragile. The id is
 * represented as a String here since jackson-module-kotlin has no built-in
 * support for the kotlin.uuid.Uuid type.
 */
data class UserResponse(
	val id: String,
	val username: String,
)

fun User.toResponse(): UserResponse = UserResponse(
	id = id.value.toString(),
	username = username,
)
