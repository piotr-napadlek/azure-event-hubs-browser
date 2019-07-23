package io.napadlek.eventhubbrowser.message

import com.fasterxml.jackson.annotation.JsonInclude
import java.nio.charset.Charset
import java.time.ZonedDateTime
import java.util.Comparator

@JsonInclude(JsonInclude.Include.NON_NULL)
data class EventHubMessage(
        val sequenceNumber: Long,
        val partitionId: String,
        val enqueuedDateTime: ZonedDateTime,
        val offset: String,
        val partitionKey: String? = null,
        val properties: Map<String, String?>,
        val selfLink: String,
        val bodyBytesBase64: String?,
        val bodyString: String?,
        val bodyJson: Any?,
        val detectedCharset: Charset?) {

    companion object {
        val enqueuedDateTimeSorter: Comparator<EventHubMessage> = Comparator
                .comparing(EventHubMessage::enqueuedDateTime)
                .thenComparing(EventHubMessage::sequenceNumber)
                .reversed()
    }
}
