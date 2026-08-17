package com.fortuneavenue.server.dao

import com.fortuneavenue.server.models.user.db.User
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository

/**
 * The only place Exposed-specific calls for users should live. Callers (UserService) work with
 * plain User entities and don't need to know transaction {} blocks or Exposed's DAO API exist
 * underneath — if the ORM ever changes, this is the one class that needs to change with it.
 */
@Repository
class UserDao {

    fun create(username: String): User = transaction { User.new { this.username = username } }

    fun findById(id: Uuid): User? = transaction { User.findById(id) }
}
