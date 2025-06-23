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
@RequiredArgsConstructor
public class KafkaMonitorService {
    private final KafkaClusterManager clusterManager;

    public Flux<Map.Entry<String, String>> listClusters() {
        return Flux.fromIterable(clusterManager.getClusterInfo().entrySet());
    }

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



    public Mono<Void> updateConsumerGroupOffset(String clusterName, String groupId, String topic, int partition, long offset) {
        return Mono.fromCallable(() -> {
            try {
                // Verify topic exists and get partition count
                TopicDescription topicDesc;
                try {
                    topicDesc = clusterManager.getAdminClient(clusterName).describeTopics(Collections.singleton(topic))
                        .topicNameValues()
                        .get(topic)
                        .get();
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof UnknownTopicOrPartitionException) {
                        throw new IllegalArgumentException("Topic '" + topic + "' does not exist");
                    }
                    throw e;
                }

                if (partition < 0 || partition >= topicDesc.partitions().size()) {
                    throw new IllegalArgumentException(String.format(
                        "Invalid partition number %d. Topic '%s' has %d partitions (0-%d)",
                        partition, topic, topicDesc.partitions().size(), topicDesc.partitions().size() - 1));
                }

                TopicPartition topicPartition = new TopicPartition(topic, partition);

                // Verify offset is within bounds
                Map<TopicPartition, Long> endOffsets = clusterManager.getConsumer(clusterName).endOffsets(Collections.singletonList(topicPartition));
                Map<TopicPartition, Long> beginningOffsets = clusterManager.getConsumer(clusterName).beginningOffsets(Collections.singletonList(topicPartition));
                long endOffset = endOffsets.get(topicPartition);
                long beginningOffset = beginningOffsets.get(topicPartition);

                if (offset < beginningOffset || offset > endOffset) {
                    throw new IllegalArgumentException(
                        String.format("Offset %d is out of range. Valid range is %d to %d",
                            offset, beginningOffset, endOffset));
                }

                // Update the offset
                Map<TopicPartition, OffsetAndMetadata> offsetMap = Collections.singletonMap(
                    topicPartition,
                    new OffsetAndMetadata(offset)
                );
                try {
                    clusterManager.getAdminClient(clusterName).alterConsumerGroupOffsets(groupId, offsetMap).all().get();
                    return null;
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof UnknownMemberIdException) {
                        throw new IllegalStateException("Consumer group '" + groupId + "' does not exist");
                    }
                    throw e;
                }
            } catch (ExecutionException e) {
                throw new RuntimeException("Failed to update consumer group offset", e);
            }
        });
    }
}
