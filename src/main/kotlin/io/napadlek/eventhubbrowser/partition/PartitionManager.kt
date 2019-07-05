package io.napadlek.eventhubbrowser.partition

import com.microsoft.azure.eventhubs.EventHubClient
import io.napadlek.eventhubbrowser.hub.EventHubConnectionManager
import io.napadlek.eventhubbrowser.hub.EventHubStats
import io.napadlek.eventhubbrowser.pmap
import org.springframework.stereotype.Component
import org.springframework.web.context.annotation.SessionScope
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

@Component
@SessionScope
class PartitionManager(val hubConnectionManager: EventHubConnectionManager) {

    fun getPartitionInfos(hubId: String): List<PartitionInfo> {
        val (eventHubConnection, eventHubClient, _) = hubConnectionManager.getHubConnectionConfig(hubId)
        return eventHubConnection.partitionIds.pmap(eventHubConnection.partitionCount?.let { it / 2 } ?: 1) {
            partitionRuntimeInformation(eventHubClient, it, hubId)
        }.sortedByDescending { it.lastEnqueuedDateTime }
    }

    fun getPartitionInfo(hubId: String, partitionId: String): PartitionInfo {
        val (_, eventHubClient, _) = hubConnectionManager.getHubConnectionConfig(hubId)
        return partitionRuntimeInformation(eventHubClient, partitionId, hubId)
    }

    private fun partitionRuntimeInformation(eventHubClient: EventHubClient, partitionId: String, hubId: String): PartitionInfo {
        val partitionRuntimeInformation = eventHubClient.getPartitionRuntimeInformation(partitionId).get(10, TimeUnit.SECONDS)
        return PartitionInfo(partitionRuntimeInformation.partitionId, partitionRuntimeInformation.lastEnqueuedSequenceNumber,
                partitionRuntimeInformation.lastEnqueuedTimeUtc.atZone(ZoneOffset.UTC), partitionRuntimeInformation.lastEnqueuedOffset,
                partitionRuntimeInformation.beginSequenceNumber,
                (partitionRuntimeInformation.lastEnqueuedSequenceNumber - Math.max(partitionRuntimeInformation.beginSequenceNumber, 0)) + 1,
                "/hubs/$hubId/partitions/$partitionId/messages/${partitionRuntimeInformation.lastEnqueuedSequenceNumber}",
                "/hubs/$hubId/partitions/$partitionId",
                "/hubs/$hubId/partitions/$partitionId/messages")
    }


    fun getHubStats(hubId: String): EventHubStats {
        val partitionInfos = getPartitionInfos(hubId)
        val activeEvents = partitionInfos.map { it.activeEventsCount }.sum()
        val lastEnqueued = partitionInfos.firstOrNull()?.lastEnqueuedDateTime
        return EventHubStats(activeEvents, lastEnqueued)
    }
}
