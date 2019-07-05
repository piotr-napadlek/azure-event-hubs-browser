package io.napadlek.eventhubbrowser.message

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.azure.eventhubs.EventData
import com.microsoft.azure.eventhubs.EventPosition
import com.microsoft.azure.eventhubs.PartitionReceiver
import io.napadlek.eventhubbrowser.hub.EventHubConnectionManager
import io.napadlek.eventhubbrowser.partition.PartitionInfo
import io.napadlek.eventhubbrowser.partition.PartitionManager
import org.mozilla.universalchardet.UniversalDetector
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.context.annotation.SessionScope
import org.springframework.web.server.ResponseStatusException
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.charset.UnsupportedCharsetException
import java.time.Duration
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import kotlin.math.min

@Component
@SessionScope
class PartitionReader(val partitionManager: PartitionManager,
                      val connectionManager: EventHubConnectionManager,
                      val mapper: ObjectMapper) {

    companion object {
        const val MAX_BATCH = 500
    }

    val partitionMessages: MutableMap<String, MutableMap<String, Triple<PartitionReceiver, PartitionReceiverPosition, MutableMap<Long, EventData>>>> = ConcurrentHashMap()

    fun getAllMessages(hubId: String, partitionId: String): List<EventHubMessage> {
        val partitionInfo = partitionManager.getPartitionInfo(hubId, partitionId)
        ensurePartitionReceiver(hubId, partitionId, partitionInfo)
        val (partitionReceiver, receiverPosition, messagesMap) = partitionMessages[hubId]!![partitionId]!!
        if (receiverPosition.currentSequenceNumber < partitionInfo.lastEnqueuedSequenceNumber) {
            receiveMessages(partitionReceiver, partitionInfo.lastEnqueuedSequenceNumber, messagesMap, receiverPosition)
        }
        return partitionMessages[hubId]!![partitionId]!!.third.values
                .map { convertToEventHubMessage(it, partitionId, hubId, false) }
                .sortedByDescending { it.enqueuedDateTime }
    }

    fun getMessageBySequenceNumber(hubId: String, partitionId: String, sequenceNumber: Long, includeBody: Boolean = true): EventHubMessage {
        val partitionInfo = partitionManager.getPartitionInfo(hubId, partitionId)
        ensurePartitionReceiver(hubId, partitionId, partitionInfo)
        val (partitionReceiver, receiverPosition, messagesMap) = partitionMessages[hubId]!![partitionId]!!

        if (receiverPosition.currentSequenceNumber < sequenceNumber) {
            receiveMessages(partitionReceiver, sequenceNumber, messagesMap, receiverPosition)
        }
        val eventData = partitionMessages[hubId]!![partitionId]!!.third[sequenceNumber]
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found")
        return convertToEventHubMessage(eventData, partitionId, hubId, includeBody)
    }

    private fun receiveMessages(partitionReceiver: PartitionReceiver, targetSequenceNumber: Long, messagesMap: MutableMap<Long, EventData>, receiverPosition: PartitionReceiverPosition) {
        do {
            val messagesReceived = partitionReceiver.receiveSync(min(MAX_BATCH, (targetSequenceNumber - receiverPosition.currentSequenceNumber).toInt()))
            messagesReceived?.forEach {
                messagesMap[it.systemProperties.sequenceNumber] = it
            }
            messagesReceived?.lastOrNull()?.let{ receiverPosition.currentSequenceNumber = it.systemProperties.sequenceNumber }
        } while (receiverPosition.currentSequenceNumber < targetSequenceNumber && messagesReceived?.count() ?: 0 > 0)
    }

    private fun ensurePartitionReceiver(hubId: String, partitionId: String, partitionInfo: PartitionInfo) {
        if (hubId !in partitionMessages) partitionMessages[hubId] = ConcurrentHashMap()
        if (partitionId !in partitionMessages[hubId]!!) {
            val (eventHubConnection, eventHubClient) = connectionManager.getHubConnectionConfig(hubId)
            val receiver = eventHubClient.createReceiverSync(eventHubConnection.consumerGroupName, partitionId,
                    EventPosition.fromStartOfStream())
            receiver.receiveTimeout = Duration.ofSeconds(20)
            partitionMessages[hubId]!![partitionId] = Triple(receiver, PartitionReceiverPosition(partitionInfo.beginSequenceNumber - 1),
                    ConcurrentHashMap())
        }
    }

    fun convertToEventHubMessage(eventData: EventData, partitionId: String, hubId: String, includeBody: Boolean = true): EventHubMessage {
        val (bodyBase64, bodyString, bodyJson, charset) = if (includeBody) readEventContent(eventData) else BodyRepresentation()
        return EventHubMessage(
                eventData.systemProperties.sequenceNumber, partitionId,
                eventData.systemProperties.enqueuedTime.atZone(ZoneOffset.UTC),
                eventData.systemProperties.offset, eventData.systemProperties.partitionKey,
                eventData.properties.map { it.key to it.value?.toString() }.toMap(),
                "/hubs/$hubId/partitions/$partitionId/messages/${eventData.systemProperties.sequenceNumber}",
                bodyBase64, bodyString, bodyJson, charset)
    }

    private fun readEventContent(data: EventData): BodyRepresentation {
        if (data.bytes == null) {
            return BodyRepresentation(null, null, null, null)
        }
        val bodyBase64 = Base64.getEncoder().encode(data.bytes).toString(StandardCharsets.UTF_8)
        val bodyBytes = if (data.properties["ContentEncoding"] == "gzip") GZIPInputStream(data.bytes.inputStream()).readAllBytes() else data.bytes
        val detectedCharset: Charset? = try {
            detectBodyCharset(bodyBytes.inputStream())
        } catch (ex: UnsupportedCharsetException) {
            null
        }
        val bodyString: String? = try {
            detectedCharset?.let { bodyBytes.toString(it) } ?: bodyBytes.toString(StandardCharsets.UTF_8)
        } catch (ex: Exception) {
            null
        }
        val bodyJson = try {
            mapper.readTree(bodyString)
        } catch (ex: JsonParseException) {
            null
        }
        return BodyRepresentation(bodyBase64, bodyString, bodyJson, detectedCharset)
    }

    private fun detectBodyCharset(bodyStream: InputStream): Charset? {
        val charsetDetector = UniversalDetector(null)
        var nread: Int
        val buf = ByteArray(4096)

        while (bodyStream.available() > 0 && !charsetDetector.isDone) {
            nread = bodyStream.read(buf)
            charsetDetector.handleData(buf, 0, nread)
        }
        charsetDetector.dataEnd()
        return charsetDetector.detectedCharset?.let { Charset.forName(it) }
    }
}

data class BodyRepresentation(
        val base64: String? = null,
        val string: String? = null,
        val json: JsonNode? = null,
        val charset: Charset? = null
)

data class PartitionReceiverPosition(
        var currentSequenceNumber: Long = -1
)