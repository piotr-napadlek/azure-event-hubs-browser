package io.napadlek.eventhubbrowser.hub

import com.microsoft.azure.eventhubs.*
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.ScopedProxyMode
import org.springframework.stereotype.Component
import org.springframework.web.context.annotation.SessionScope
import java.time.Duration
import java.time.ZoneOffset
import java.util.concurrent.*
import java.util.concurrent.TimeoutException
import javax.annotation.PreDestroy

@Component
@SessionScope(proxyMode = ScopedProxyMode.TARGET_CLASS)
class EventHubConnectionManager {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val hubConnections: MutableMap<String, Triple<EventHubConnection, EventHubClient, ScheduledExecutorService>> = ConcurrentHashMap()

    @PreDestroy
    internal fun closeClients() {
        logger.info("Deregistering event hub connections...")
        hubConnections.values.forEach(this::closeEventHubClient)
    }

    fun getHubConnections() = hubConnections.values.map { updateHubConnectionStatus(it.second, it.first) }

    fun createHubConnection(definition: EventHubDefinition): EventHubConnection {
        val hubId = "${definition.eventHubNamespace}/${definition.eventHubName}"
        if (hubId in hubConnections) {
            throw EventHubConnectionExistsException(hubId)
        }

        val scheduledThreadPoolExecutor = ScheduledThreadPoolExecutor(10)
        val client = createEventHubClient(definition, scheduledThreadPoolExecutor)
        val eventHubConnection = createBaseEventHubConnectionObject(definition)
        updateHubConnectionStatus(client, eventHubConnection)
        hubConnections[eventHubConnection.id] = Triple(eventHubConnection, client, scheduledThreadPoolExecutor)
        return eventHubConnection
    }

    fun deleteHubConnection(id: String): Boolean {
        hubConnections[id]?.let(this::closeEventHubClient)
        return hubConnections.remove(id)?.let { true } ?: false
    }

    fun updateHubConnection(definition: EventHubDefinition, id: String): EventHubConnection {
        if (!deleteHubConnection(id)) {
            throw EventHubConnectionNotFoundException(id)
        }
        return createHubConnection(definition)
    }

    fun getHubConnectionDetails(id: String): EventHubConnection {
        val connectionTriple = getHubConnectionConfig(id)
        updateHubConnectionStatus(connectionTriple.second, connectionTriple.first)
        return connectionTriple.first
    }

    internal fun getHubConnectionConfig(id: String) = hubConnections[id] ?: throw EventHubConnectionNotFoundException(id)


    private fun closeEventHubClient(config: Triple<EventHubConnection, EventHubClient, ScheduledExecutorService>) {
        config.second.closeSync()
        logger.info("Client for ${config.first.id} closed, shutting down executor service...")
        config.third.shutdown()
        config.third.awaitTermination(10, TimeUnit.SECONDS)
        logger.info("Executor service ${config.first.id} closed.")
    }

    private fun updateHubConnectionStatus(client: EventHubClient, eventHubConnection: EventHubConnection): EventHubConnection {
        try {
            val runtimeInformation = client.runtimeInformation.orTimeout(10, TimeUnit.SECONDS).get()
            eventHubConnection.status = EventHubConnectionStatus.CONNECTED
            eventHubConnection.partitionCount = runtimeInformation.partitionCount
            eventHubConnection.createdDateTime = runtimeInformation.createdAt.atZone(ZoneOffset.UTC)
            eventHubConnection.partitionIds = runtimeInformation.partitionIds.toList()
            eventHubConnection.partitionsLink = "/hubs/${eventHubConnection.id}/partitions"
            eventHubConnection.statsLink = "/hubs/${eventHubConnection.id}/stats"
        } catch (t: Throwable) {
            when (t.cause) {
                is AuthorizationFailedException -> eventHubConnection.status = EventHubConnectionStatus.UNAUTHORIZED
                is TimeoutException, is com.microsoft.azure.eventhubs.TimeoutException -> eventHubConnection.status = EventHubConnectionStatus.CONNECTION_TIMEOUT
                else -> eventHubConnection.status = EventHubConnectionStatus.FAILED
            }
        }
        return eventHubConnection
    }

    private fun createEventHubClient(definition: EventHubDefinition, scheduledThreadPoolExecutor: ScheduledThreadPoolExecutor): EventHubClient {
        val connectionString = ConnectionStringBuilder()
                .setNamespaceName(definition.eventHubNamespace)
                .setEventHubName(definition.eventHubName)
                .setSasKeyName(definition.sasKeyName)
                .setSasKey(definition.sasKey)
                .setOperationTimeout(Duration.ofSeconds(30))
                .setTransportType(TransportType.AMQP)
        return EventHubClient.create(connectionString.toString(), RetryPolicy.getNoRetry(), scheduledThreadPoolExecutor).get()
    }

    private fun createBaseEventHubConnectionObject(definition: EventHubDefinition): EventHubConnection {
        return EventHubConnection(
                id = "${definition.eventHubNamespace}/${definition.eventHubName}",
                eventHubName = definition.eventHubName,
                eventHubNamespace = definition.eventHubNamespace,
                selfLink = "/hubs/${definition.eventHubNamespace}/${definition.eventHubName}",
                consumerGroupName = definition.consumerGroupName,
                sasKeyName = definition.sasKeyName,
                sasKeyFirst4Characters = definition.sasKey.substring(0..4)
        )
    }

}