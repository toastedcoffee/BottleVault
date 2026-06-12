package com.bottlevault

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

// Scheduling drives the daily purge of expired refresh tokens.
@SpringBootApplication
@EnableScheduling
class BottleVaultApplication

fun main(args: Array<String>) {
    runApplication<BottleVaultApplication>(*args)
}
