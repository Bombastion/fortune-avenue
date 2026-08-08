package com.fortuneavenue.server.models.player.rest

/**
 * [userId] is optional and a String (rather than kotlin.uuid.Uuid, which
 * jackson-module-kotlin can't deserialize) -- omit it to add a
 * not-yet-implemented computer opponent instead of a person.
 */
data class AddPlayerRequest(
	val userId: String? = null,
)
