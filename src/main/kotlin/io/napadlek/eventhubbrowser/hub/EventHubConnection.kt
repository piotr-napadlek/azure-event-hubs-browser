package io.napadlek.eventhubbrowser.hub

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import java.time.ZonedDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
data class EventHubConnection(
        val id: String,
        val eventHubNamespace: String,
        val eventHubName: String,
        val selfLink: String,
        val consumerGroupName: String,
        val sasKeyName: String,
        val sasKeyFirst4Characters: String,
        var status: EventHubConnectionStatus? = null,
        var partitionCount: Int? = null,
        var createdDateTime: ZonedDateTime? = null,
        var partitionsLink: String? = null,
        var statsLink: String? = null,
        @JsonIgnore var partitionIds: List<String> = emptyList()
)

data class EventHubStats(
        val activeEventsCount: Long,
        val lastEnqueuedDateTime: ZonedDateTime?
)