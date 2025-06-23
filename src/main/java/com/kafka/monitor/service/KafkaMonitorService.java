package com.kafka.monitor.service;

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
import org.apache.kafka.common.errors.UnknownMemberIdException;
import org.apache.kafka.common.config.ConfigResource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KafkaMonitorService {
    private final KafkaConsumer<String, String> kafkaConsumer;
    private final AdminClient adminClient;

    public Flux<String> listClusters() {
        // In a real-world scenario, you would maintain a list of Kafka clusters
        // For demo purposes, we'll return the default cluster
        return Flux.just("localhost:9092");
    }

    public Flux<String> listTopics() {
        return Mono.fromCallable(() -> 
            adminClient.listTopics()
                .listings()
                .get()
                .stream()
                .map(TopicListing::name)
                .collect(Collectors.toList()))
            .flatMapMany(Flux::fromIterable);
    }

    public Mono<TopicInfo> getTopicInfo(String topicName) {
        return Mono.fromCallable(() -> {
            // Get topic partitions
            var partitions = adminClient.describeTopics(Collections.singleton(topicName))
                .allTopicNames()
                .get()
                .get(topicName)
                .partitions();

            // Get topic configs
            var configs = adminClient.describeConfigs(
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
            Map<TopicPartition, Long> endOffsets = kafkaConsumer.endOffsets(topicPartitions);
            Map<TopicPartition, Long> beginningOffsets = kafkaConsumer.beginningOffsets(topicPartitions);

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

            return getConsumerGroupsForTopic(topicName, finalEndOffsets)
                .collectList()
                .map(consumerGroups -> TopicInfo.builder()
                    .name(topicName)
                    .partitions(finalPartitionInfos)
                    .consumerGroups(consumerGroups)
                    .configs(finalConfigs)
                    .build());
        }).flatMap(mono -> mono);
    }

    private Flux<ConsumerGroupInfo> getConsumerGroupsForTopic(String topicName, Map<TopicPartition, Long> endOffsets) {
        return Mono.fromCallable(() -> 
            adminClient.listConsumerGroups()
                .all()
                .get()
                .stream()
                .map(ConsumerGroupListing::groupId)
                .collect(Collectors.toList()))
            .flatMapMany(groups -> Flux.fromIterable(groups)
                .flatMap(groupId -> getConsumerGroupInfo(groupId, topicName, endOffsets)));
    }

    private Mono<ConsumerGroupInfo> getConsumerGroupInfo(String groupId, String topicName, Map<TopicPartition, Long> endOffsets) {
        return Mono.fromCallable(() -> {
            var description = adminClient.describeConsumerGroups(Collections.singleton(groupId))
                .all()
                .get()
                .get(groupId);

            var offsets = adminClient.listConsumerGroupOffsets(groupId)
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

    @PreDestroy
    public void cleanup() {
        kafkaConsumer.close();
    }

    public Mono<Void> updateConsumerGroupOffset(String groupId, String topic, int partition, long offset) {
        return Mono.fromCallable(() -> {
            TopicPartition topicPartition = new TopicPartition(topic, partition);
            Map<TopicPartition, OffsetAndMetadata> offsetMap = Collections.singletonMap(
                topicPartition,
                new OffsetAndMetadata(offset)
            );

            try {
                // Verify the topic partition exists
                Map<TopicPartition, Long> endOffsets = kafkaConsumer.endOffsets(Collections.singletonList(topicPartition));
                if (!endOffsets.containsKey(topicPartition)) {
                    throw new IllegalArgumentException("Topic partition does not exist");
                }

                // Verify offset is within bounds
                long endOffset = endOffsets.get(topicPartition);
                Map<TopicPartition, Long> beginningOffsets = kafkaConsumer.beginningOffsets(Collections.singletonList(topicPartition));
                long beginningOffset = beginningOffsets.get(topicPartition);

                if (offset < beginningOffset || offset > endOffset) {
                    throw new IllegalArgumentException(
                        String.format("Offset %d is out of range. Valid range is %d to %d",
                            offset, beginningOffset, endOffset));
                }

                // Update the offset
                adminClient.alterConsumerGroupOffsets(groupId, offsetMap).all().get();
                return null;
            } catch (ExecutionException e) {
                if (e.getCause() instanceof UnknownMemberIdException) {
                    throw new IllegalStateException("Consumer group " + groupId + " does not exist");
                }
                throw new RuntimeException("Failed to update consumer group offset", e);
            }
        });
    }
}
