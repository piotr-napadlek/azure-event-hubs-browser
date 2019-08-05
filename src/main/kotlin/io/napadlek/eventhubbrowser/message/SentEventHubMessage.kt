package io.napadlek.eventhubbrowser.message

data class SentEventHubMessage(
        val bodyString: String?,
        val properties: Map<String, Any?>?,
        val partitionKey: String?
)