package com.fortuneavenue.server.rest

import com.fortuneavenue.server.models.common.rest.ErrorResponse
import com.fortuneavenue.server.models.common.rest.SortDirection
import com.fortuneavenue.server.models.common.rest.map
import com.fortuneavenue.server.models.user.rest.CreateUserRequest
import com.fortuneavenue.server.models.user.rest.UserResponse
import com.fortuneavenue.server.models.user.rest.toResponse
import com.fortuneavenue.server.service.UserService
import kotlin.uuid.Uuid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users")
class UserController(private val userService: UserService) {

    @PostMapping
    fun createUser(@RequestBody request: CreateUserRequest): ResponseEntity<UserResponse> {
        val user = userService.createUser(request.username)
        return ResponseEntity.status(HttpStatus.CREATED).body(user.toResponse())
    }

    @GetMapping
    fun listUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
        @RequestParam(defaultValue = "ASC") direction: String,
    ): ResponseEntity<Any> {
        val sortDirection =
            SortDirection.entries.firstOrNull { it.name == direction }
                ?: return ResponseEntity.badRequest()
                    .body<Any>(
                        ErrorResponse(
                            "direction must be one of ${SortDirection.entries.map { it.name }}."
                        )
                    )

        val result =
            userService.listUsers(page = page, pageSize = pageSize, direction = sortDirection)

        return result.fold(
            onSuccess = { usersPage -> ResponseEntity.ok<Any>(usersPage.map { it.toResponse() }) },
            onFailure = { error ->
                ResponseEntity.badRequest()
                    .body<Any>(ErrorResponse(error.message ?: "Invalid pagination request."))
            },
        )
    }

    @GetMapping("/{id}")
    fun getUser(@PathVariable id: String): ResponseEntity<UserResponse> {
        val userId = Uuid.parseOrNull(id) ?: return ResponseEntity.badRequest().build()

        val user = userService.getUser(userId) ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(user.toResponse())
    }
}
