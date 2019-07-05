package io.napadlek.eventhubbrowser.session

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.servlet.http.HttpSessionEvent
import javax.servlet.http.HttpSessionListener

@Configuration
class SessionListener : HttpSessionListener {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun sessionCreated(event: HttpSessionEvent) {
        event.session.maxInactiveInterval = 130 * 1000
        logger.info("Session created: sessionId: ${event.session.id}, createdAt: ${LocalDateTime.ofInstant(Instant.ofEpochMilli(event.session.creationTime), ZoneOffset.UTC)}")
    }

    override fun sessionDestroyed(event: HttpSessionEvent?) {
        logger.info("session invalidated: sessionId: ${event?.session?.id}, " +
                "lastUsed: ${LocalDateTime.ofInstant(Instant.ofEpochMilli(event?.session?.lastAccessedTime ?: 0), ZoneOffset.UTC)}")
    }
}
