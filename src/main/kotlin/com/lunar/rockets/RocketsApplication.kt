package com.lunar.rockets

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class RocketsApplication

fun main(args: Array<String>) {
	runApplication<RocketsApplication>(*args)
}
