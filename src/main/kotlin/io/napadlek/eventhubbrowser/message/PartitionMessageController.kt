package io.napadlek.eventhubbrowser.message

import io.napadlek.eventhubbrowser.hubId
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("/hubs/{hubNamespace}/{hubName}/partitions/{partitionId}/messages")
class PartitionMessageController(val partitionReader: PartitionReader) {

    @GetMapping("{sequenceNumber}")
    fun getMessageBySequenceNumber(@PathVariable hubNamespace: String,
                                   @PathVariable hubName: String,
                                   @PathVariable partitionId: String,
                                   @PathVariable sequenceNumber: Long,
                                   @RequestParam(required = false, defaultValue = "true") includeBody: Boolean): EventHubMessage {
        return partitionReader.getMessageBySequenceNumber(hubId(hubNamespace, hubName), partitionId, sequenceNumber, includeBody)
    }

    @GetMapping
    fun getAllMessages(@PathVariable hubNamespace: String,
                       @PathVariable hubName: String,
                       @PathVariable partitionId: String) = partitionReader.getAllMessages(hubId(hubNamespace, hubName), partitionId)

}