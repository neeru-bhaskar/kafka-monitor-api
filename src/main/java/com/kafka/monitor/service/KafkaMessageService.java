package com.kafka.monitor.service;

import com.kafka.monitor.config.KafkaClusterManager;
import com.kafka.monitor.model.MessageResponse;
import com.kafka.monitor.model.MessageSearchRequest;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.FluxSink;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Service for Kafka message retrieval operations.
 * Provides functionality to retrieve individual messages and search through messages.
 */
@Service
public class KafkaMessageService {
    private final KafkaClusterManager clusterManager;

    public KafkaMessageService(KafkaClusterManager clusterManager) {
        this.clusterManager = clusterManager;
    }

    /**
     * Validates that a topic and partition exist and are valid.
     *
     * @param adminClient Kafka admin client
     * @param topic Topic name to validate
     * @param partition Partition number to validate
     * @throws IllegalArgumentException if topic or partition is invalid
     */
    private void validateTopicPartition(AdminClient adminClient, String topic, int partition) {
        try {
            DescribeTopicsResult describeResult = adminClient.describeTopics(
                Collections.singleton(topic));
            TopicDescription topicDesc = describeResult.allTopicNames().get().get(topic);

            if (topicDesc == null) {
                throw new IllegalArgumentException("Topic '" + topic + "' does not exist");
            }

            if (partition < 0 || partition >= topicDesc.partitions().size()) {
                throw new IllegalArgumentException("Invalid partition number " + partition + 
                    " for topic '" + topic + "'. Valid range: 0-" + (topicDesc.partitions().size() - 1));
            }
        } catch (InterruptedException | ExecutionException e) {
            if (e.getCause() instanceof UnknownTopicOrPartitionException) {
                throw new IllegalArgumentException("Topic '" + topic + "' does not exist");
            }
            throw new RuntimeException("Failed to validate topic and partition", e);
        }
    }

    /**
     * Validates that an offset is within valid range for a topic partition.
     *
     * @param clusterName Name of the Kafka cluster
     * @param topic Topic name
     * @param partition Partition number
     * @param offset Offset to validate
     * @throws IllegalArgumentException if offset is out of valid range
     */
    private void validateOffset(String clusterName, String topic, int partition, long offset) {
        try {
            TopicPartition topicPartition = new TopicPartition(topic, partition);
            KafkaConsumer<String, String> consumer = clusterManager.getConsumer(clusterName);
            Map<TopicPartition, Long> beginningOffsets = consumer.beginningOffsets(
                Collections.singleton(topicPartition));
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(
                Collections.singleton(topicPartition));

            long beginningOffset = beginningOffsets.get(topicPartition);
            long endOffset = endOffsets.get(topicPartition);

            if (offset < beginningOffset || offset >= endOffset) {
                throw new IllegalArgumentException("Invalid offset " + offset + 
                    " for topic '" + topic + "' partition " + partition + 
                    ". Valid range: " + beginningOffset + "-" + (endOffset - 1));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to validate offset: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves a single message from a specific offset in a topic partition.
     *
     * @param clusterName Name of the Kafka cluster
     * @param topic Topic name
     * @param partition Partition number
     * @param offset Message offset
     * @return Message data if found
     */
    public Mono<MessageResponse> getMessage(String clusterName, String topic, int partition, long offset) {
        return Mono.fromCallable(() -> {
            AdminClient adminClient = clusterManager.getAdminClient(clusterName);
            // Validate topic and partition
            validateTopicPartition(adminClient, topic, partition);
            validateOffset(clusterName, topic, partition, offset);

            KafkaConsumer<String, String> consumer = clusterManager.getConsumer(clusterName);
            TopicPartition topicPartition = new TopicPartition(topic, partition);
            consumer.assign(Collections.singleton(topicPartition));
            consumer.seek(topicPartition, offset);

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            if (records.isEmpty()) {
                throw new IllegalArgumentException("No message found at offset " + offset);
            }

            ConsumerRecord<String, String> record = records.records(topicPartition).iterator().next();
            return MessageResponse.builder()
                    .topic(record.topic())
                    .partition(record.partition())
                    .offset(record.offset())
                    .timestamp(Instant.ofEpochMilli(record.timestamp()))
                    .key(record.key())
                    .value(record.value())
                    .clusterName(clusterName)
                    .build();
        });
    }

    /**
     * Searches for messages in a topic based on various criteria.
     *
     * @param clusterName Name of the Kafka cluster
     * @param searchRequest Search criteria
     * @return Flux of messages matching the criteria (max 50 messages)
     */
    public Flux<MessageResponse> searchMessages(String clusterName, MessageSearchRequest searchRequest) {
        return Flux.create(sink -> {
            try {
                AdminClient adminClient = clusterManager.getAdminClient(clusterName);
                // Validate topic and partition
                validateTopicPartition(adminClient, searchRequest.getTopic(), 
                        searchRequest.getPartition() != null ? searchRequest.getPartition() : 0);

                KafkaConsumer<String, String> consumer = clusterManager.getConsumer(clusterName);
                List<TopicPartition> partitions;
                
                if (searchRequest.getPartition() != null) {
                    partitions = Collections.singletonList(
                        new TopicPartition(searchRequest.getTopic(), searchRequest.getPartition()));
                } else {
                    partitions = consumer.partitionsFor(searchRequest.getTopic()).stream()
                        .map(p -> new TopicPartition(searchRequest.getTopic(), p.partition()))
                        .collect(Collectors.toList());
                }

                Map<TopicPartition, Long> beginningOffsets = consumer.beginningOffsets(partitions);
                Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);

                for (TopicPartition partition : partitions) {
                    long startOffset = searchRequest.getStartOffset() != null ?
                        searchRequest.getStartOffset() :
                        beginningOffsets.get(partition);
                    long endOffset = searchRequest.getEndOffset() != null ?
                        Math.min(searchRequest.getEndOffset(), endOffsets.get(partition)) :
                        endOffsets.get(partition);

                    if (startOffset >= endOffset) continue;

                    consumer.assign(Collections.singleton(partition));
                    consumer.seek(partition, startOffset);

                    int messageCount = 0;
                    while (messageCount < 50) {
                        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                        if (records.isEmpty()) break;

                        for (ConsumerRecord<String, String> record : records) {
                            if (record.offset() > endOffset) continue;
                            Instant messageTimestamp = Instant.ofEpochMilli(record.timestamp());
                            if (searchRequest.getEndTimestamp() != null && 
                                messageTimestamp.isAfter(searchRequest.getEndTimestamp())) continue;
                            if (searchRequest.getStartTimestamp() != null && 
                                messageTimestamp.isBefore(searchRequest.getStartTimestamp())) continue;

                            messageCount++;
                            sink.next(MessageResponse.builder()
                                .topic(record.topic())
                                .partition(record.partition())
                                .offset(record.offset())
                                .timestamp(Instant.ofEpochMilli(record.timestamp()))
                                .key(record.key())
                                .value(record.value())
                                .clusterName(clusterName)
                                .build());

                            if (messageCount >= 50) break;
                        }
                    }
                }

                sink.complete();
            } catch (Exception e) {
                sink.error(e);
            }
        }, FluxSink.OverflowStrategy.BUFFER);
    }
}
