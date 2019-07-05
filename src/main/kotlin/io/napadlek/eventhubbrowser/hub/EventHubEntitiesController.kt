package io.napadlek.eventhubbrowser.hub

import io.napadlek.eventhubbrowser.hubId
import io.napadlek.eventhubbrowser.partition.PartitionManager
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("hubs")
class EventHubEntitiesController(val manager: EventHubConnectionManager, val partitionsManager: PartitionManager) {

    @GetMapping
    fun getHubConnections() = manager.getHubConnections()

    @GetMapping("/{namespace}/{name}")
    fun getHubConnection(@PathVariable namespace: String, @PathVariable name: String): EventHubConnection
            = manager.getHubConnectionDetails("$namespace/$name")

    @PostMapping
    fun createHubConnection(@RequestBody definition: EventHubDefinition) = manager.createHubConnection(definition)

    @PutMapping("/{namespace}/{name}")
    fun updateHubConnection(@PathVariable namespace: String, @PathVariable name: String, @RequestBody definition: EventHubDefinition)
            = manager.updateHubConnection(definition, "$namespace/$name")

    @DeleteMapping("/{namespace}/{name}")
    fun deleteHubConnection(@PathVariable namespace: String, @PathVariable name: String): ResponseEntity<Void>
            = if (manager.deleteHubConnection("$namespace/$name")) ResponseEntity.ok().build() else ResponseEntity.noContent().build()

    @GetMapping("/{namespace}/{name}/stats")
    fun getHubStats(@PathVariable namespace: String, @PathVariable name: String) = partitionsManager.getHubStats(hubId(namespace, name))

}
