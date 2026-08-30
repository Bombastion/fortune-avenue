package com.fortuneavenue.server.dao

import com.fortuneavenue.server.models.user.db.User
import com.fortuneavenue.server.models.user.db.UsersTable
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.selectAll
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

    /** Users are sorted by username until we add sort criteria. */
    fun findPage(page: Int, pageSize: Int, ascending: Boolean = true): List<User> = transaction {
        val sortOrder = if (ascending) SortOrder.ASC else SortOrder.DESC

        val query =
            UsersTable.selectAll()
                .orderBy(UsersTable.username, sortOrder)
                .limit(pageSize)
                .offset(page.toLong() * pageSize)

        User.wrapRows(query).toList()
    }

    /**
     * Total number of users, regardless of any page/pageSize -- used to compute how many pages
     * [findPage] has. Will eventually need to make this take search criteria, but we don't have any
     * yet.
     */
    fun count(): Long = transaction { UsersTable.selectAll().count() }
}
