package io.napadlek.eventhubbrowser.message

import io.napadlek.eventhubbrowser.hubId
import org.springframework.http.ResponseEntity
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/hubs/{hubNamespace}/{hubName}/messages")
class MessageController(private val partitionReader: PartitionReader, private val messageSender: MessageSender) {

    @GetMapping("/query")
    fun queryMessages(@PathVariable hubNamespace: String,
                      @PathVariable hubName: String,
                      @RequestParam(required = false, defaultValue = "false") includeBody: Boolean,
                      @RequestParam(required = false) bodyFormat: BodyFormat?,
                      @RequestParam queryMap: MultiValueMap<String, String>): List<EventHubMessage> {
        queryMap["fromSequenceNumber"] = listOf("-1")
        queryMap["toSequenceNumber"] = listOf(Long.MAX_VALUE.toString())
        val messageQueryParams = MessageQueryParams(queryMap)
        val bodyFormats = getBodyFormats(includeBody, bodyFormat)
        return partitionReader.queryMessages(hubId(hubNamespace, hubName), messageQueryParams, bodyFormats)
    }

    @PostMapping
    fun sendMessage(@PathVariable hubNamespace: String,
                    @PathVariable hubName: String,
                    @RequestBody sentEventHubMessage: SentEventHubMessage) : ResponseEntity<Void> {
        messageSender.sendMessage(hubId(hubNamespace, hubName), sentEventHubMessage)
        return ResponseEntity.accepted().build()
    }
}
