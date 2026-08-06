package com.fortuneavenue.server.rest

import com.fortuneavenue.server.models.user.rest.CreateUserRequest
import com.fortuneavenue.server.models.user.rest.UserResponse
import com.fortuneavenue.server.models.user.rest.toResponse
import com.fortuneavenue.server.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.uuid.Uuid

@RestController
@RequestMapping("/users")
class UserController(
	private val userService: UserService,
) {

	@PostMapping
	fun createUser(@RequestBody request: CreateUserRequest): ResponseEntity<UserResponse> {
		val user = userService.createUser(request.username)
		return ResponseEntity.status(HttpStatus.CREATED).body(user.toResponse())
	}

	@GetMapping("/{id}")
	fun getUser(@PathVariable id: String): ResponseEntity<UserResponse> {
		val userId = Uuid.parseOrNull(id) ?: return ResponseEntity.badRequest().build()

		val user = userService.getUser(userId) ?: return ResponseEntity.notFound().build()

		return ResponseEntity.ok(user.toResponse())
	}
}
