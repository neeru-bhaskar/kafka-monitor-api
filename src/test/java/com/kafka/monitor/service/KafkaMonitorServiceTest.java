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

    @BeforeEach
    void setUp() {
        service = new KafkaMonitorService(clusterManager);
    }

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

        Map<TopicPartition, ListOffsetsResultInfo> offsetResults = Map.of(
            new TopicPartition(topicName, 0), 
            new ListOffsetsResultInfo(0L, 0L, Optional.empty()));
        ListOffsetsResult listOffsetsResult = mock(ListOffsetsResult.class);
        when(listOffsetsResult.all()).thenReturn(KafkaFuture.completedFuture(offsetResults));

        when(adminClient.describeTopics(Set.of(topicName))).thenReturn(describeTopicsResult);
        when(adminClient.describeConfigs(any())).thenReturn(configsResult);
        when(adminClient.listOffsets(anyMap())).thenReturn(listOffsetsResult);
        when(clusterManager.getAdminClient("local")).thenReturn(adminClient);

        // When
        Mono<TopicInfo> result = service.getTopicInfo("local", topicName);

        // Then
        StepVerifier.create(result)
            .expectNextMatches(topicInfo -> 
                topicInfo.getName().equals(topicName) &&
                topicInfo.getPartitions().size() == 1 &&
                topicInfo.getPartitions().get(0).getPartition() == 0)
            .verifyComplete();
    }

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
