package io.napadlek.eventhubbrowser

import com.fasterxml.jackson.databind.ObjectMapper
import io.napadlek.eventhubbrowser.hub.EventHubConnection
import io.napadlek.eventhubbrowser.hub.EventHubConnectionStatus
import io.napadlek.eventhubbrowser.hub.EventHubStats
import io.napadlek.eventhubbrowser.message.EventHubMessage
import io.napadlek.eventhubbrowser.message.SentEventHubMessage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpRequest
import org.springframework.http.ResponseEntity
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import spock.lang.Shared
import spock.lang.Specification

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EventhubBrowserApplicationE2eSpec extends Specification {

    @Autowired
    TestEventHubEntityManager testEventHubEntityManager

    @Autowired
    ObjectMapper objectMapper

    TestRestTemplate testRestTemplate

    @Shared
    Set<String> cookies = new HashSet<>()

    @Autowired
    void setTestRestTemplate(TestRestTemplate testRestTemplate) {
        testRestTemplate.restTemplate.interceptors.add(new ClientHttpRequestInterceptor() {
            @Override
            ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) {
                cookies.each { request.headers.add("Cookie", String.valueOf(it)) }
                def response = execution.execute(request, body)
                cookies.addAll(response.headers.getOrDefault("Set-Cookie", []))
                return response
            }
        })
        this.testRestTemplate = testRestTemplate
    }

    def "message is sent and received by created event hub connection"() {
        given:
        def hubDefinition = testEventHubEntityManager.connectionDefinition

        and:
        def sampleMessageBody = "{\"key\": \"value\"}"
        def sentEvent = new SentEventHubMessage(sampleMessageBody, ["propertyKey": "Value"], "PK123")

        when:
        def postHubResponseEntity = testRestTemplate.postForEntity("/hubs", hubDefinition, EventHubConnection)

        then:
        postHubResponseEntity.statusCode.is2xxSuccessful()
        postHubResponseEntity.body != null
        postHubResponseEntity.body.status == EventHubConnectionStatus.CONNECTED
        postHubResponseEntity.body.partitionCount == TestEventHubEntityManagerKt.PARTITION_COUNT

        when:
        def hubId = postHubResponseEntity.body.id
        def getHubsResponseEntity = testRestTemplate.getForEntity("/hubs", List) as ResponseEntity<List<EventHubConnection>>;

        then:
        getHubsResponseEntity.statusCode.is2xxSuccessful()
        def getHubsBody = getHubsResponseEntity.body
        getHubsBody.size() == 1
        def hubConnection = getHubsBody.get(0)
        hubConnection.id == hubId
        hubConnection.status == EventHubConnectionStatus.CONNECTED.name()

        when:
        def getHubStatsResponseEntity = testRestTemplate.getForEntity("/hubs/${hubId}/stats", EventHubStats) as ResponseEntity<EventHubStats>

        then:
        getHubStatsResponseEntity.statusCode.is2xxSuccessful()
        getHubStatsResponseEntity.body.activeEventsCount == 0

        when:

        def postEventResponseEntity = testRestTemplate.postForEntity("/hubs/${hubId}/messages", sentEvent, Void)

        then:
        postEventResponseEntity.statusCode.is2xxSuccessful()

        when:
        getHubStatsResponseEntity = testRestTemplate.getForEntity("/hubs/${hubId}/stats", EventHubStats) as ResponseEntity<EventHubStats>

        then:
        getHubStatsResponseEntity.statusCode.is2xxSuccessful()
        getHubStatsResponseEntity.body.activeEventsCount == 1

        when:
        def messageQueryResponseEntity = testRestTemplate.getForEntity("/hubs/${hubId}/messages/query", List) as ResponseEntity<List<EventHubMessage>>;

        then:
        messageQueryResponseEntity.statusCode.is2xxSuccessful()
        messageQueryResponseEntity.body.size() == 1
        def foundMessage = messageQueryResponseEntity.body.get(0)
        "propertyKey" in foundMessage.properties
        foundMessage.properties["propertyKey"] == "Value"
        foundMessage.partitionKey == "PK123"

        when:
        def messageResponseEntity = testRestTemplate.getForEntity(foundMessage.selfLink + "?includeBody=true", EventHubMessage)

        then:
        messageResponseEntity.statusCode.is2xxSuccessful()
        messageResponseEntity.body.bodyBytesBase64 == Base64.encoder.encodeToString(sentEvent.bodyString.bytes)
        messageResponseEntity.body.bodyString == sentEvent.bodyString
        messageResponseEntity.body.bodyJson.key == "value"
    }
}
