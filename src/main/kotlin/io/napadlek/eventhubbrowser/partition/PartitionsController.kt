package io.napadlek.eventhubbrowser.partition

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/hubs/{hubNamespace}/{hubName}/partitions")
class PartitionsController(val partitionManager: PartitionManager) {

    @GetMapping
    fun getPartitionInfos(@PathVariable hubNamespace: String, @PathVariable hubName: String): List<PartitionInfo> {
        return partitionManager.getPartitionInfos("$hubNamespace/$hubName")
    }

    @GetMapping("/{partitionId}")
    fun getPartitionInfo(@PathVariable hubNamespace: String, @PathVariable hubName: String, @PathVariable partitionId: String): PartitionInfo {
        return partitionManager.getPartitionInfo("$hubNamespace/$hubName", partitionId)
    }
}
