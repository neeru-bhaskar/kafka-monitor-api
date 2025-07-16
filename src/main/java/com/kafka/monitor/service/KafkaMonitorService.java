package com.kafka.monitor.service;

import com.kafka.monitor.config.KafkaClusterManager;
import com.kafka.monitor.model.ConsumerGroupInfo;
import com.kafka.monitor.model.ConsumerPartitionInfo;
import com.kafka.monitor.model.PartitionInfo;
import com.kafka.monitor.model.TopicInfo;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.UnknownMemberIdException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
/**
 * Service layer for Kafka monitoring operations.
 * Provides functionality to interact with multiple Kafka clusters through AdminClient.
 * Supports cluster management, topic operations, and consumer group offset management.
 */
public class KafkaMonitorService {
    private final KafkaClusterManager clusterManager;

    public KafkaMonitorService(KafkaClusterManager clusterManager) {
        this.clusterManager = clusterManager;
    }

    /**
     * List all configured Kafka clusters.
     * 
     * @return Flux of cluster name and bootstrap server pairs
     */
    public Flux<Map.Entry<String, String>> listClusters() {
        return Flux.fromIterable(clusterManager.getClusters().entrySet());
    }

    /**
     * List all topics in a specified Kafka cluster.
     * 
     * @param clusterName name of the Kafka cluster
     * @return Flux of topic names
     */
    public Flux<String> listTopics(String clusterName) {
        return Mono.fromCallable(() -> 
            clusterManager.getAdminClient(clusterName).listTopics()
                .listings()
                .get()
                .stream()
                .map(TopicListing::name)
                .collect(Collectors.toList()))
            .flatMapMany(Flux::fromIterable);
    }

    /**
     * Get detailed information about a specific topic.
     * 
     * @param clusterName name of the Kafka cluster
     * @param topicName name of the topic
     * @return Mono containing topic details including partitions, configuration, and offsets
     */
    public Mono<TopicInfo> getTopicInfo(String clusterName, String topicName) {
        return Mono.fromCallable(() -> {
            // Get topic partitions
            var partitions = clusterManager.getAdminClient(clusterName).describeTopics(Collections.singleton(topicName))
                .allTopicNames()
                .get()
                .get(topicName)
                .partitions();

            // Get topic configs
            var configs = clusterManager.getAdminClient(clusterName).describeConfigs(
                Collections.singleton(new ConfigResource(ConfigResource.Type.TOPIC, topicName)))
                .all()
                .get()
                .values()
                .iterator()
                .next()
                .entries()
                .stream()
                .collect(Collectors.toMap(ConfigEntry::name, ConfigEntry::value));

            // Build partition info with latest offsets
            List<PartitionInfo> partitionInfos = new ArrayList<>();
            List<TopicPartition> topicPartitions = new ArrayList<>();
            
            for (var partition : partitions) {
                TopicPartition tp = new TopicPartition(topicName, partition.partition());
                topicPartitions.add(tp);
            }

            // Get end offsets for all partitions
            Map<TopicPartition, Long> endOffsets = clusterManager.getConsumer(clusterName).endOffsets(topicPartitions);
            Map<TopicPartition, Long> beginningOffsets = clusterManager.getConsumer(clusterName).beginningOffsets(topicPartitions);

            for (var partition : partitions) {
                TopicPartition tp = new TopicPartition(topicName, partition.partition());
                partitionInfos.add(PartitionInfo.builder()
                    .partition(partition.partition())
                    .leader(partition.leader().id())
                    .replicas(partition.replicas().stream().mapToInt(r -> r.id()).toArray())
                    .inSyncReplicas(partition.isr().stream().mapToInt(r -> r.id()).toArray())
                    .beginningOffset(beginningOffsets.get(tp))
                    .endOffset(endOffsets.get(tp))
                    .build());
            }

            // Prepare data for consumer groups
            Map<TopicPartition, Long> finalEndOffsets = endOffsets;
            List<PartitionInfo> finalPartitionInfos = partitionInfos;
            Map<String, String> finalConfigs = configs;

            return getConsumerGroupsForTopic(clusterName, topicName, finalEndOffsets)
                .collectList()
                .map(consumerGroups -> TopicInfo.builder()
                    .name(topicName)
                    .partitions(finalPartitionInfos)
                    .consumerGroups(consumerGroups)
                    .configs(finalConfigs)
                    .build());
        }).flatMap(mono -> (Mono<TopicInfo>) mono);
    }

    private Flux<ConsumerGroupInfo> getConsumerGroupsForTopic(String clusterName, String topicName, Map<TopicPartition, Long> endOffsets) {
        return Mono.fromCallable(() -> 
            clusterManager.getAdminClient(clusterName).listConsumerGroups()
                .all()
                .get()
                .stream()
                .map(ConsumerGroupListing::groupId)
                .collect(Collectors.toList()))
            .flatMapMany(groups -> Flux.fromIterable(groups)
                .flatMap(groupId -> getConsumerGroupInfo(clusterName, groupId, topicName, endOffsets)));
    }

    private Mono<ConsumerGroupInfo> getConsumerGroupInfo(String clusterName, String groupId, String topicName, Map<TopicPartition, Long> endOffsets) {
        return Mono.fromCallable(() -> {
            var description = clusterManager.getAdminClient(clusterName).describeConsumerGroups(Collections.singleton(groupId))
                .all()
                .get()
                .get(groupId);

            var offsets = clusterManager.getAdminClient(clusterName).listConsumerGroupOffsets(groupId)
                .partitionsToOffsetAndMetadata()
                .get();

            Map<Integer, ConsumerPartitionInfo> partitionOffsets = new HashMap<>();
            
            offsets.forEach((topicPartition, offsetMetadata) -> {
                if (topicPartition.topic().equals(topicName)) {
                    var member = description.members().stream()
                        .filter(m -> m.assignment().topicPartitions().contains(topicPartition))
                        .findFirst()
                        .orElse(null);

                    long currentOffset = offsetMetadata.offset();
                    long endOffset = endOffsets.getOrDefault(topicPartition, 0L);
                    long lag = Math.max(0, endOffset - currentOffset);

                    partitionOffsets.put(topicPartition.partition(),
                        ConsumerPartitionInfo.builder()
                            .currentOffset(currentOffset)
                            .endOffset(endOffset)
                            .lag(lag)
                            .consumerId(member != null ? member.consumerId() : null)
                            .clientId(member != null ? member.clientId() : null)
                            .host(member != null ? member.host() : null)
                            .build());
                }
            });

            return ConsumerGroupInfo.builder()
                .groupId(groupId)
                .state(description.state().toString())
                .partitionOffsets(partitionOffsets)
                .build();
        });
    }



    /**
     * Update consumer group offset for a specific topic partition.
     * 
     * @param clusterName name of the Kafka cluster
     * @param groupId consumer group ID
     * @param topic topic name
     * @param partition partition number
     * @param offset new offset value
     * @return Mono<Void> completing when offset is updated
     * @throws IllegalArgumentException if offset is out of range or partition is invalid
     */
    public Mono<Void> updateConsumerGroupOffset(String clusterName, String groupId, String topic, int partition, long offset) {
        AdminClient adminClient = clusterManager.getAdminClient(clusterName);
        
        // First validate topic and partition
        return Mono.create(sink -> {
            try {
                adminClient.describeTopics(Collections.singleton(topic))
                    .allTopicNames()
                    .get()
                    .values()
                    .stream()
                    .findFirst()
                    .ifPresentOrElse(
                        topicDesc -> {
                            if (partition < 0 || partition >= topicDesc.partitions().size()) {
                                sink.error(new IllegalArgumentException(String.format(
                                    "Invalid partition number %d. Topic '%s' has %d partitions (0-%d)",
                                    partition, topic, topicDesc.partitions().size(),
                                    topicDesc.partitions().size() - 1)));
                                return;
                            }

                            TopicPartition topicPartition = new TopicPartition(topic, partition);

                            // Check if consumer group exists
                            adminClient.describeConsumerGroups(Collections.singleton(groupId))
                                .all()
                                .whenComplete((groupDesc, error) -> {
                                    if (error != null) {
                                        sink.error(error);
                                        return;
                                    }
                                    if (groupDesc.isEmpty()) {
                                        sink.error(new IllegalStateException("Consumer group '" + groupId + "' does not exist"));
                                        return;
                                    }

                                    // Validate offset bounds
                                    var earliestOffsetsFuture = adminClient.listOffsets(
                                        Collections.singletonMap(topicPartition, OffsetSpec.earliest())
                                    ).all();
                                    var latestOffsetsFuture = adminClient.listOffsets(
                                        Collections.singletonMap(topicPartition, OffsetSpec.latest())
                                    ).all();

                                    earliestOffsetsFuture.whenComplete((beginningOffsets, e1) -> {
                                        if (e1 != null) {
                                            sink.error(e1);
                                            return;
                                        }
                                        latestOffsetsFuture.whenComplete((endOffsets, e2) -> {
                                            if (e2 != null) {
                                                sink.error(e2);
                                                return;
                                            }

                                            Long beginningOffset = beginningOffsets.get(topicPartition).offset();
                                            Long endOffset = endOffsets.get(topicPartition).offset();

                                            if (beginningOffset == null || endOffset == null) {
                                                sink.error(new IllegalStateException("Failed to get offset bounds"));
                                                return;
                                            }

                                            if (offset < beginningOffset || offset > endOffset) {
                                                sink.error(new IllegalArgumentException(
                                                    String.format("Offset %d is out of range. Valid range is %d to %d",
                                                        offset, beginningOffset, endOffset)));
                                                return;
                                            }

                                            // Update offset
                                            Map<TopicPartition, OffsetAndMetadata> offsetMap = Collections.singletonMap(
                                                topicPartition,
                                                new OffsetAndMetadata(offset)
                                            );

                                            adminClient.alterConsumerGroupOffsets(groupId, offsetMap)
                                                .all()
                                                .whenComplete((result, e3) -> {
                                                    if (e3 != null) {
                                                        sink.error(e3);
                                                    } else {
                                                        sink.success();
                                                    }
                                                });
                                        });
                                    });
                                });
                        },
                        () -> sink.error(new IllegalArgumentException("Topic '" + topic + "' does not exist"))
                    );
            } catch (ExecutionException e) {
                if (e.getCause() instanceof UnknownTopicOrPartitionException) {
                    sink.error(new IllegalArgumentException("Topic '" + topic + "' does not exist"));
                } else if (e.getCause() instanceof UnknownMemberIdException) {
                    sink.error(new IllegalStateException("Consumer group '" + groupId + "' does not exist"));
                } else {
                    sink.error(new RuntimeException("Failed to update consumer group offset", e));
                }
            } catch (InterruptedException e) {
                sink.error(new RuntimeException("Operation interrupted", e));
                Thread.currentThread().interrupt();
            }
        });

    }
}
