package io.napadlek.eventhubbrowser.message

import io.napadlek.eventhubbrowser.hubId
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("/hubs/{hubNamespace}/{hubName}/partitions/{partitionId}/messages")
class PartitionMessageController(val partitionReader: PartitionReader) {

    @GetMapping("{sequenceNumber}")
    fun getMessageBySequenceNumber(@PathVariable hubNamespace: String,
                                   @PathVariable hubName: String,
                                   @PathVariable partitionId: String,
                                   @PathVariable sequenceNumber: Long,
                                   @RequestParam(required = false, defaultValue = "false") includeBody: Boolean,
                                   @RequestParam(required = false) bodyFormat: BodyFormat?): EventHubMessage {
        return partitionReader.getMessageBySequenceNumber(hubId(hubNamespace, hubName), partitionId, sequenceNumber, getBodyFormats(includeBody, bodyFormat))
    }

    @GetMapping
    fun getAllMessages(@PathVariable hubNamespace: String,
                       @PathVariable hubName: String,
                       @PathVariable partitionId: String,
                       @RequestParam(required = false, defaultValue = "false") includeBody: Boolean,
                       @RequestParam(required = false) bodyFormat: BodyFormat?): List<EventHubMessage> {
        return partitionReader.getAllMessages(hubId(hubNamespace, hubName), partitionId, getBodyFormats(includeBody, bodyFormat))
    }

    @GetMapping("query")
    fun queryMessages(@PathVariable hubNamespace: String,
                      @PathVariable hubName: String,
                      @PathVariable partitionId: String,
                      @RequestParam(required = false, defaultValue = "false") includeBody: Boolean,
                      @RequestParam(required = false) bodyFormat: BodyFormat?,
                      @RequestParam queryMap: MultiValueMap<String, String>): List<EventHubMessage> {
        val messageQueryParams = MessageQueryParams(queryMap)
        return partitionReader.queryMessages(hubId(hubNamespace, hubName), partitionId, messageQueryParams, getBodyFormats(includeBody, bodyFormat))
    }

    private fun getBodyFormats(includeBody: Boolean, targetFormat: BodyFormat?): Set<BodyFormat> {
        val includedBodyFormats = if (includeBody) targetFormat?.let { arrayOf(it) } ?: BodyFormat.values() else emptyArray()
        return includedBodyFormats.toSet()
    }
}
