package com.fortuneavenue.server.service

import com.fortuneavenue.server.dao.UserDao
import com.fortuneavenue.server.models.common.rest.Page
import com.fortuneavenue.server.models.common.rest.SortDirection
import com.fortuneavenue.server.models.user.db.User
import kotlin.uuid.Uuid
import org.springframework.stereotype.Service

@Service
class UserService(private val userDao: UserDao) {

    fun createUser(username: String): User = userDao.create(username)

    fun getUser(id: Uuid): User? = userDao.findById(id)

    fun listUsers(
        page: Int,
        pageSize: Int,
        direction: SortDirection = SortDirection.ASC,
    ): Result<Page<User>> {
        if (page < 0) return Result.failure(InvalidUserException("page must be zero or greater."))
        if (pageSize < 1) {
            return Result.failure(InvalidUserException("pageSize must be at least 1."))
        }

        val items =
            userDao.findPage(
                page = page,
                pageSize = pageSize,
                ascending = direction == SortDirection.ASC,
            )
        val totalItems = userDao.count()

        return Result.success(
            Page.of(
                items = items,
                page = page,
                pageSize = pageSize,
                direction = direction,
                totalItems = totalItems,
            )
        )
    }
}
