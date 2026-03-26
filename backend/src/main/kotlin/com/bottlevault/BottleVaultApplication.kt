package com.bottlevault

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BottleVaultApplication

fun main(args: Array<String>) {
    runApplication<BottleVaultApplication>(*args)
}
