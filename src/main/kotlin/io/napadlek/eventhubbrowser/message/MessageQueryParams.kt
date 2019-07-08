package io.napadlek.eventhubbrowser.message

import org.springframework.util.MultiValueMap
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

data class MessageQueryParams(private val queryProperties: MultiValueMap<String, String>) {

    val timestampRange: ClosedRange<ZonedDateTime>
    val sequenceNumberRange: LongRange
    val offsetRange: LongRange
    val partitionKey: String?
    val properties: Map<String, String?>?

    init {
        val fromTimestamp = queryProperties["fromTimestamp"]?.firstOrNull()?.let { ZonedDateTime.parse(it) }
                ?: LocalDateTime.MIN.atZone(ZoneOffset.UTC)
        val toTimestamp = queryProperties["toTimestamp"]?.firstOrNull()?.let { ZonedDateTime.parse(it) }
                ?: LocalDateTime.MAX.atZone(ZoneOffset.UTC)
        timestampRange = fromTimestamp..toTimestamp
        sequenceNumberRange = (queryProperties["fromSequenceNumber"]?.firstOrNull()?.toLong() ?: -1L)..
                (queryProperties["toSequenceNumber"]?.firstOrNull()?.toLong() ?: Long.MAX_VALUE)
        offsetRange = (queryProperties["fromOffset"]?.firstOrNull()?.toLong() ?: 0)..(queryProperties["toOffset"]?.firstOrNull()?.toLong() ?: Long.MAX_VALUE)
        partitionKey = queryProperties["partitionKey"]?.firstOrNull()
        properties = queryProperties["property"]?.map {
            val stringSplit = it.split("=", limit = 2)
            stringSplit[0] to stringSplit.getOrNull(1)
        }?.toMap()
    }
}
