package com.fortuneavenue.server.service

import com.fortuneavenue.server.dao.UserDao
import com.fortuneavenue.server.models.user.db.User
import org.springframework.stereotype.Service
import kotlin.uuid.Uuid

@Service
class UserService(
	private val userDao: UserDao,
) {

	fun createUser(username: String): User = userDao.create(username)

	fun getUser(id: Uuid): User? = userDao.findById(id)
}
