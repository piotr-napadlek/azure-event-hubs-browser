package io.napadlek.eventhubbrowser.message

import com.microsoft.azure.eventhubs.EventData
import io.napadlek.eventhubbrowser.hub.EventHubConnectionManager
import org.springframework.stereotype.Component

@Component
class MessageSender(
        private val eventHubConnectionManager: EventHubConnectionManager
) {

    fun sendMessage(hubId: String, sentEventHubMessage: SentEventHubMessage) {
        val ehClient = eventHubConnectionManager.getHubConnectionConfig(hubId).second

        val event = EventData.create(sentEventHubMessage.bodyString?.toByteArray() ?: "".toByteArray())
        sentEventHubMessage.properties?.let { event.properties.putAll(it) }
        if (sentEventHubMessage.partitionKey != null) {
            ehClient.sendSync(event, sentEventHubMessage.partitionKey)
        } else {
            ehClient.sendSync(event)
        }
    }
}