package com.kafka.monitor.service;

import com.kafka.monitor.config.KafkaClusterManager;
import com.kafka.monitor.model.TopicInfo;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.config.ConfigResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.*;
import java.util.HashMap;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.TopicListing;
import org.apache.kafka.common.errors.ApiException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class KafkaMonitorServiceTest {

    @Mock
    private KafkaClusterManager clusterManager;

    @Mock
    private AdminClient adminClient;

    @Mock
    private KafkaConsumer<String, String> kafkaConsumer;

    private KafkaMonitorService service;

    /**
     * Set up test environment before each test.
     * Initializes service with mocked cluster manager.
     */
    @BeforeEach
    void setUp() {
        service = new KafkaMonitorService(clusterManager);
    }

    /**
     * Test that listClusters() returns a Flux of cluster name and bootstrap server pairs.
     * Verifies that cluster information is correctly retrieved from cluster manager.
     */
    @Test
    void listClusters_ShouldReturnClusterList() {
        // Given
        Map<String, String> clusters = Map.of("local", "localhost:9092");
        when(clusterManager.getClusterInfo()).thenReturn(clusters);

        // When
        Flux<Map.Entry<String, String>> result = service.listClusters();

        // Then
        StepVerifier.create(result)
            .expectNext(Map.entry("local", "localhost:9092"))
            .verifyComplete();
    }

    /**
     * Test that listTopics() returns a Flux of topic names for a given cluster.
     * Verifies that topic listings are correctly retrieved through AdminClient.
     */
    @Test
    void listTopics_ShouldReturnTopicsList() {
        // Given
        ListTopicsResult listTopicsResult = mock(ListTopicsResult.class);
        KafkaFuture<Collection<TopicListing>> listingsFuture = KafkaFuture.completedFuture(
            Arrays.asList(
                new TopicListing("orders", false),
                new TopicListing("notifications", false)
            ));
        when(listTopicsResult.listings()).thenReturn(listingsFuture);
        when(adminClient.listTopics()).thenReturn(listTopicsResult);
        when(clusterManager.getAdminClient("local")).thenReturn(adminClient);

        // When
        Flux<String> result = service.listTopics("local");

        // Then
        StepVerifier.create(result)
            .expectNext("orders")
            .expectNext("notifications")
            .verifyComplete();
    }

    /**
     * Test that getTopicInfo() returns detailed topic information including partitions and offsets.
     * Verifies that topic description, configuration, and offset information are correctly retrieved.
     */
    @Test
    void getTopicInfo_ShouldReturnTopicDetails() {
        // Given
        String topicName = "test-topic";
        Node leader = new Node(1, "localhost", 9092);
        TopicPartitionInfo partitionInfo = new TopicPartitionInfo(
                0, leader, List.of(leader), List.of(leader));
        
        Map<String, TopicDescription> descriptions = Map.of(
            topicName, new TopicDescription(topicName, false, List.of(partitionInfo)));
        DescribeTopicsResult describeTopicsResult = mock(DescribeTopicsResult.class);
        when(describeTopicsResult.allTopicNames()).thenReturn(KafkaFuture.completedFuture(descriptions));

        Map<ConfigResource, org.apache.kafka.clients.admin.Config> configMap = Map.of(
            new ConfigResource(ConfigResource.Type.TOPIC, topicName),
            new org.apache.kafka.clients.admin.Config(Collections.emptyList()));
        DescribeConfigsResult configsResult = mock(DescribeConfigsResult.class);
        when(configsResult.all()).thenReturn(KafkaFuture.completedFuture(configMap));

        // Mock earliest offsets
        TopicPartition topicPartition = new TopicPartition(topicName, 0);
        ListOffsetsResult earliestResult = mock(ListOffsetsResult.class);
        ListOffsetsResult latestResult = mock(ListOffsetsResult.class);
        ListOffsetsResultInfo earliestInfo = new ListOffsetsResultInfo(0L, 0L, Optional.empty());
        ListOffsetsResultInfo latestInfo = new ListOffsetsResultInfo(100L, 0L, Optional.empty());

        when(earliestResult.all()).thenReturn(KafkaFuture.completedFuture(
            Map.of(topicPartition, earliestInfo)));
        when(latestResult.all()).thenReturn(KafkaFuture.completedFuture(
            Map.of(topicPartition, latestInfo)));

        when(adminClient.describeTopics(Set.of(topicName))).thenReturn(describeTopicsResult);
        when(adminClient.describeConfigs(any())).thenReturn(configsResult);
        when(adminClient.listOffsets(Map.of(topicPartition, OffsetSpec.earliest()))).thenReturn(earliestResult);
        when(adminClient.listOffsets(Map.of(topicPartition, OffsetSpec.latest()))).thenReturn(latestResult);
        when(clusterManager.getAdminClient("local")).thenReturn(adminClient);

        // When
        Mono<TopicInfo> result = service.getTopicInfo("local", topicName);

        // Then
        StepVerifier.create(result)
            .expectNextMatches(topicInfo -> 
                topicInfo.getName().equals(topicName) &&
                topicInfo.getPartitions().size() == 1 &&
                topicInfo.getPartitions().get(0).getPartition() == 0 &&
                topicInfo.getPartitions().get(0).getBeginningOffset() == 0 &&
                topicInfo.getPartitions().get(0).getEndOffset() == 100)
            .verifyComplete();
    }

    /**
     * Test that updateConsumerGroupOffset() successfully updates consumer group offset.
     * Verifies that offset update is performed through AdminClient after validation.
     */
    @Test
    void updateConsumerGroupOffset_ShouldUpdateOffset() {
        // Given
        String groupId = "test-group";
        String topic = "test-topic";
        int partition = 0;
        long offset = 100L;

        // Mock cluster manager
        when(clusterManager.getAdminClient("local")).thenReturn(adminClient);

        // Mock describe consumer groups
        DescribeConsumerGroupsResult describeResult = mock(DescribeConsumerGroupsResult.class);
        ConsumerGroupDescription groupDesc = mock(ConsumerGroupDescription.class);
        when(describeResult.all()).thenReturn(KafkaFuture.completedFuture(
            Map.of(groupId, groupDesc)));
        when(adminClient.describeConsumerGroups(Set.of(groupId))).thenReturn(describeResult);

        // Mock list offsets for validation
        TopicPartition topicPartition = new TopicPartition(topic, partition);
        ListOffsetsResult earliestResult = mock(ListOffsetsResult.class);
        ListOffsetsResult latestResult = mock(ListOffsetsResult.class);
        ListOffsetsResultInfo earliestInfo = new ListOffsetsResultInfo(0L, 0L, Optional.empty());
        ListOffsetsResultInfo latestInfo = new ListOffsetsResultInfo(200L, 0L, Optional.empty());

        when(earliestResult.all()).thenReturn(KafkaFuture.completedFuture(
            Map.of(topicPartition, earliestInfo)));
        when(latestResult.all()).thenReturn(KafkaFuture.completedFuture(
            Map.of(topicPartition, latestInfo)));
        when(adminClient.listOffsets(Map.of(topicPartition, OffsetSpec.earliest()))).thenReturn(earliestResult);
        when(adminClient.listOffsets(Map.of(topicPartition, OffsetSpec.latest()))).thenReturn(latestResult);

        // Mock alter offsets
        AlterConsumerGroupOffsetsResult alterResult = mock(AlterConsumerGroupOffsetsResult.class);
        when(alterResult.all()).thenReturn(KafkaFuture.completedFuture(null));
        when(adminClient.alterConsumerGroupOffsets(eq(groupId), any())).thenReturn(alterResult);

        // When
        Mono<Void> result = service.updateConsumerGroupOffset("local", groupId, topic, partition, offset);

        // Then
        StepVerifier.create(result)
            .verifyComplete();
    }

    /**
     * Test that updateConsumerGroupOffset() returns error for invalid offset value.
     * Verifies that offset validation fails for negative offset and returns appropriate error.
     */
    @Test
    void updateConsumerGroupOffset_WithInvalidOffset_ShouldReturnError() {
        // Given
        String groupId = "test-group";
        String topic = "test-topic";
        int partition = 0;
        long offset = -1L;

        // Mock cluster manager
        when(clusterManager.getAdminClient("local")).thenReturn(adminClient);

        // Mock describe consumer groups
        DescribeConsumerGroupsResult describeResult = mock(DescribeConsumerGroupsResult.class);
        ConsumerGroupDescription groupDesc = mock(ConsumerGroupDescription.class);
        when(describeResult.all()).thenReturn(KafkaFuture.completedFuture(
            Map.of(groupId, groupDesc)));
        when(adminClient.describeConsumerGroups(Set.of(groupId))).thenReturn(describeResult);

        // Mock list offsets for validation
        TopicPartition topicPartition = new TopicPartition(topic, partition);
        ListOffsetsResult earliestResult = mock(ListOffsetsResult.class);
        ListOffsetsResult latestResult = mock(ListOffsetsResult.class);
        ListOffsetsResultInfo earliestInfo = new ListOffsetsResultInfo(0L, 0L, Optional.empty());
        ListOffsetsResultInfo latestInfo = new ListOffsetsResultInfo(100L, 0L, Optional.empty());

        when(earliestResult.all()).thenReturn(KafkaFuture.completedFuture(
            Map.of(topicPartition, earliestInfo)));
        when(latestResult.all()).thenReturn(KafkaFuture.completedFuture(
            Map.of(topicPartition, latestInfo)));
        when(adminClient.listOffsets(Map.of(topicPartition, OffsetSpec.earliest()))).thenReturn(earliestResult);
        when(adminClient.listOffsets(Map.of(topicPartition, OffsetSpec.latest()))).thenReturn(latestResult);

        // When
        Mono<Void> result = service.updateConsumerGroupOffset("local", groupId, topic, partition, offset);

        // Then
        StepVerifier.create(result)
            .expectErrorMatches(throwable -> 
                throwable instanceof IllegalArgumentException &&
                throwable.getMessage().contains("Offset -1 is out of range"))
            .verify();
    }
}
