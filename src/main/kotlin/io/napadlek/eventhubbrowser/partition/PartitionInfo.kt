package io.napadlek.eventhubbrowser.partition

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.ZonedDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PartitionInfo(
        val id: String,
        val lastEnqueuedSequenceNumber: Long,
        val lastEnqueuedDateTime: ZonedDateTime,
        val lastEnqueuedOffset: String,
        val beginSequenceNumber: Long,
        val activeEventsCount: Long,
        val lastEnqueuedLink: String,
        val selfLink: String,
        val messagesLink: String
)
