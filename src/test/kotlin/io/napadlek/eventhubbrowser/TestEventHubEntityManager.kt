package io.napadlek.eventhubbrowser

import com.microsoft.azure.credentials.ApplicationTokenCredentials
import com.microsoft.azure.management.eventhub.EventHubNamespaceSkuType
import com.microsoft.azure.management.eventhub.implementation.EventHubManager
import com.microsoft.azure.management.resources.fluentcore.arm.Region
import com.microsoft.rest.LogLevel
import io.napadlek.eventhubbrowser.hub.EventHubDefinition
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

private const val TEST_NAMESPACE = "e2e-test-eh-browser-ns"
private const val TEST_NAMESPACE_RESOURCE_GROUP = "e2e-test-eh-browser-rg"
private const val TEST_HUB_NAME = "test-hub"
private const val TEST_CONSUMER_GROUP = "test-cg"
private const val SAS_KEY_NAME = "RootManageSharedAccessKey"
const val PARTITION_COUNT = 15

@Component
class TestEventHubEntityManager(
        @Value("\${auth.file.path:#{null}}") private val authFilePath: String?,
        @Autowired private val resourceLoader: ResourceLoader
) {
    private var eventHubManager: EventHubManager? = null

    val connectionDefinition by lazy {
        eventHubManager?.let {
            it.namespaceAuthorizationRules()
                    .getByName(TEST_NAMESPACE_RESOURCE_GROUP, TEST_NAMESPACE, SAS_KEY_NAME)
                    .keys.primaryKey()
        }?.let { EventHubDefinition(it, TEST_NAMESPACE, TEST_HUB_NAME, TEST_CONSUMER_GROUP, SAS_KEY_NAME) }
                ?: throw IllegalStateException("Cannot obtain connection details: namespace is not created")
    }

    @PostConstruct
    internal fun setUpTestEventhub() {
        this.eventHubManager = this.authFilePath
                ?.let { resourceLoader.getResource(it).file }
                ?.let { ApplicationTokenCredentials.fromFile(it) }
                ?.let { EventHubManager.configure().withLogLevel(LogLevel.BASIC).authenticate(it, it.defaultSubscriptionId()) }
                ?.let {
                    println("Creating namespace $TEST_NAMESPACE...")
                    it.namespaces()
                            .define(TEST_NAMESPACE)
                            .withRegion(Region.EUROPE_WEST)
                            .withNewResourceGroup(TEST_NAMESPACE_RESOURCE_GROUP)
                            .withSku(EventHubNamespaceSkuType.STANDARD)
                            .withCurrentThroughputUnits(1)
                            .withTag("TEMPORARY", "true")
                            .withTag("AUTO_DELETE", "true")
                            .withTag("SERVICE", "event-hub-browser")
                            .withNewEventHub(TEST_HUB_NAME, PARTITION_COUNT, 1)
                            .create()
                    println("Namespace with event hub created, adding consumer group $TEST_CONSUMER_GROUP...")
                    try {
                        it.consumerGroups()
                                .define(TEST_CONSUMER_GROUP)
                                .withExistingEventHub(TEST_NAMESPACE_RESOURCE_GROUP, TEST_NAMESPACE, TEST_HUB_NAME)
                                .withUserMetadata("test consumer group")
                                .create()
                    } catch (e: Exception) {
                        println("Could not create consumer group, deleting resource group $TEST_NAMESPACE_RESOURCE_GROUP!")
                        deleteResourceGroup(it)
                        throw e
                    }
                    println("Consumer group created, test event hub is ready.")
                    it
                }
    }

    @PreDestroy
    internal fun destroyTestEventHub() {
        println("Deleting resource group $TEST_NAMESPACE_RESOURCE_GROUP...")
        this.eventHubManager?.let { deleteResourceGroup(it) }
        println("Deleted resource group.")
    }

    private fun deleteResourceGroup(eventHubManager: EventHubManager) {
        println("Deleting namespace...")
        eventHubManager.namespaces().deleteByResourceGroup(TEST_NAMESPACE_RESOURCE_GROUP, TEST_NAMESPACE)
        println("Deleting resource group...")
        eventHubManager.resourceManager()
                ?.resourceGroups()
                ?.deleteByName(TEST_NAMESPACE_RESOURCE_GROUP)
    }
}
