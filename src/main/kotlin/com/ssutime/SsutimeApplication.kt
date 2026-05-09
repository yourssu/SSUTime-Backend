package com.ssutime

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SsutimeApplication

fun main(args: Array<String>) {
    runApplication<SsutimeApplication>(*args)
}
