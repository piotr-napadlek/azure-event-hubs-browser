package io.napadlek.eventhubbrowser.message

import com.microsoft.azure.eventhubs.*
import io.napadlek.eventhubbrowser.hub.EventHubConnectionManager
import io.napadlek.eventhubbrowser.partition.PartitionInfo
import io.napadlek.eventhubbrowser.partition.PartitionManager
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.context.annotation.SessionScope
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.annotation.PreDestroy
import kotlin.math.min

@Component
@SessionScope
class PartitionReader(val partitionManager: PartitionManager,
                      val connectionManager: EventHubConnectionManager,
                      val messageParser: MessageParser) {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    companion object {
        const val MAX_SINGLE_RECEIVE = 500L
    }

    private val partitionMessages: MutableMap<String, MutableMap<String, PartitionState>> = ConcurrentHashMap()

    @PreDestroy
    fun closeReceivers() {
        logger.info("Closing all ${partitionMessages.values.flatMap { it.values }.count()} receivers.")
        val closingReceivers = partitionMessages.values.flatMap { it.values }.map { it.partitionReceiver.close() }
        CompletableFuture.allOf(*closingReceivers.toTypedArray()).get(20, TimeUnit.SECONDS)
        logger.info("Partition receivers closed.")
    }

    fun getAllMessages(hubId: String, partitionId: String, bodyFormats: Set<BodyFormat>): List<EventHubMessage> {
        val partitionInfo = partitionManager.getPartitionInfo(hubId, partitionId)
        ensurePartitionReceiver(hubId, partitionId, partitionInfo)
        val (partitionReceiver, receiverPosition, messagesMap) = partitionMessages[hubId]!![partitionId]!!
        if (receiverPosition.sequenceNumber < partitionInfo.lastEnqueuedSequenceNumber) {
            receiveMessages(partitionReceiver, PartitionReceiverPosition.ofSequenceTarget(partitionInfo.lastEnqueuedSequenceNumber), messagesMap, receiverPosition)
        }
        return partitionMessages[hubId]!![partitionId]!!.messagesMap.values
                .map { convertToEventHubMessage(it, partitionId, hubId, bodyFormats) }
                .sortedByDescending { it.enqueuedDateTime }
    }

    fun getMessageBySequenceNumber(hubId: String, partitionId: String, sequenceNumber: Long, includedBodyFormats: Set<BodyFormat>): EventHubMessage {
        ensurePartitionReceiver(hubId, partitionId)
        val (partitionReceiver, receiverPosition, messagesMap) = partitionMessages[hubId]!![partitionId]!!

        if (receiverPosition.sequenceNumber < sequenceNumber) {
            receiveMessages(partitionReceiver, PartitionReceiverPosition.ofSequenceTarget(sequenceNumber), messagesMap, receiverPosition)
        }
        val eventData = partitionMessages[hubId]!![partitionId]!!.messagesMap[sequenceNumber]
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found")
        return convertToEventHubMessage(eventData, partitionId, hubId, includedBodyFormats)
    }

    fun queryMessages(hubId: String, partitionId: String, queryParams: MessageQueryParams, includedBodyFormats: Set<BodyFormat>): List<EventHubMessage> {
        val partitionInfo = partitionManager.getPartitionInfo(hubId, partitionId)
        ensurePartitionReceiver(hubId, partitionId, partitionInfo)
        val (partitionReceiver, receiverPosition, messagesMap) = partitionMessages[hubId]!![partitionId]!!
        val targetPosition = PartitionReceiverPosition(
                min(queryParams.sequenceNumberRange.last, partitionInfo.lastEnqueuedSequenceNumber),
                queryParams.timestampRange.endInclusive.toInstant(),
                queryParams.offsetRange.last)
        if (receiverPosition < targetPosition) {
            receiveMessages(partitionReceiver, targetPosition, messagesMap, receiverPosition)
        }
        return partitionMessages[hubId]!![partitionId]!!.messagesMap.values.filter { event ->
            event.systemProperties.sequenceNumber in queryParams.sequenceNumberRange
                    && event.systemProperties.offset.toLong() in queryParams.offsetRange
                    && event.systemProperties.enqueuedTime.atZone(ZoneOffset.UTC) in queryParams.timestampRange
                    && queryParams.partitionKey?.let { pk -> pk == event.systemProperties.partitionKey } ?: true
                    && queryParams.properties?.all { p -> p.key in event.properties && (p.value?.let { v -> v == event.properties[p.key] } ?: true) } ?: true }
                .map { convertToEventHubMessage(it, partitionId, hubId, includedBodyFormats) }
                .sortedByDescending { it.enqueuedDateTime }
    }

    private fun receiveMessages(partitionReceiver: PartitionReceiver, targetPosition: PartitionReceiverPosition,
                                messagesMap: MutableMap<Long, EventData>, receiverPosition: PartitionReceiverPosition) {
        do {
            val messagesReceived = partitionReceiver.receiveSync(min(MAX_SINGLE_RECEIVE, targetPosition.sequenceNumber - receiverPosition.sequenceNumber).toInt())
            messagesReceived?.forEach {
                messagesMap[it.systemProperties.sequenceNumber] = it
            }
            messagesReceived?.lastOrNull()?.let {
                receiverPosition.sequenceNumber = it.systemProperties.sequenceNumber
                receiverPosition.offset = it.systemProperties.offset.toLong()
                receiverPosition.timestamp = it.systemProperties.enqueuedTime
            }
        } while (receiverPosition < targetPosition && messagesReceived?.count() ?: 0 > 0)
    }

    private fun ensurePartitionReceiver(hubId: String, partitionId: String, partitionInfo: PartitionInfo? = null) {
        if (hubId !in partitionMessages) partitionMessages[hubId] = ConcurrentHashMap()
        if (partitionId !in partitionMessages[hubId]!!) {
            val (eventHubConnection, eventHubClient) = connectionManager.getHubConnectionConfig(hubId)
            val receiverOptions = ReceiverOptions()
            receiverOptions.prefetchCount = 1999
            try {
                val receiver = eventHubClient.createReceiverSync(eventHubConnection.consumerGroupName, partitionId,
                        EventPosition.fromStartOfStream(), receiverOptions)
                receiver.receiveTimeout = Duration.ofSeconds(20)
                partitionMessages[hubId]!![partitionId] = PartitionState(receiver, PartitionReceiverPosition(
                        (partitionInfo ?: partitionManager.getPartitionInfo(hubId, partitionId)).beginSequenceNumber - 1),
                        ConcurrentHashMap())
            } catch (e: ReceiverDisconnectedException) {
                logger.warn("Could not create receiver. Is consumer group not in use?")
                throw ResponseStatusException(HttpStatus.CONFLICT, "Can't connect to partition $partitionId on hub $hubId with consumer group " +
                        "${eventHubConnection.consumerGroupName}. Is it in use already?")
            }
        }
    }

    private fun convertToEventHubMessage(eventData: EventData, partitionId: String, hubId: String, includedBodyFormats: Set<BodyFormat>): EventHubMessage {
        val (bodyBase64, bodyString, bodyJson, charset) = messageParser.readEventContent(eventData, includedBodyFormats)
        return EventHubMessage(
                eventData.systemProperties.sequenceNumber, partitionId,
                eventData.systemProperties.enqueuedTime.atZone(ZoneOffset.UTC),
                eventData.systemProperties.offset, eventData.systemProperties.partitionKey,
                eventData.properties.map { it.key to it.value?.toString() }.toMap(),
                "/hubs/$hubId/partitions/$partitionId/messages/${eventData.systemProperties.sequenceNumber}",
                bodyBase64, bodyString, bodyJson, charset)
    }
}

private data class PartitionReceiverPosition(
        var sequenceNumber: Long = -1,
        var timestamp: Instant = Instant.MIN,
        var offset: Long = 0
) : Comparable<PartitionReceiverPosition> {
    override fun compareTo(other: PartitionReceiverPosition): Int {
        val sequenceComparison = sequenceNumber.compareTo(other.sequenceNumber)
        val offsetComparison = offset.compareTo(other.offset)
        val timestampComparison = timestamp.compareTo(other.timestamp)
        return arrayOf(sequenceComparison, offsetComparison, timestampComparison).max()!!
    }

    companion object {
        fun ofSequenceTarget(sequenceNumber: Long): PartitionReceiverPosition {
            return PartitionReceiverPosition(sequenceNumber, Instant.MAX, Long.MAX_VALUE)
        }
    }
}

private data class PartitionState(
        val partitionReceiver: PartitionReceiver,
        val partitionReceiverPosition: PartitionReceiverPosition,
        val messagesMap: MutableMap<Long, EventData>
)
