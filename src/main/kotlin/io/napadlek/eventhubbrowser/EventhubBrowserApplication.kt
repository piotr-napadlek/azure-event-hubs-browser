package io.napadlek.eventhubbrowser

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootApplication
class EventhubBrowserApplication

fun main(args: Array<String>) {
	runApplication<EventhubBrowserApplication>(*args)
}
