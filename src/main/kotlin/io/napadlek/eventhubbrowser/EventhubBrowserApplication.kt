package io.napadlek.eventhubbrowser

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class EventhubBrowserApplication

fun main(args: Array<String>) {
	runApplication<EventhubBrowserApplication>(*args)
}
