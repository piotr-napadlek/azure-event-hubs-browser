package io.napadlek.eventhubbrowser.hub

data class EventHubDefinition(
        val sasKey: String,
        val eventHubNamespace: String,
        val eventHubName: String,
        val consumerGroupName: String,
        val sasKeyName: String
)
