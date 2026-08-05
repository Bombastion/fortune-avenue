package com.fortuneavenue.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FortuneAvenueServerApplication

fun main(args: Array<String>) {
	runApplication<FortuneAvenueServerApplication>(*args)
}
