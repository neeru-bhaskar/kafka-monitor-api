package com.kafka.monitor.service;

import com.kafka.monitor.model.*;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
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

            // Build partition info
            List<PartitionInfo> partitionInfos = new ArrayList<>();
            for (var partition : partitions) {
                partitionInfos.add(PartitionInfo.builder()
                    .partition(partition.partition())
                    .leader(partition.leader().id())
                    .replicas(partition.replicas().stream().mapToInt(r -> r.id()).toArray())
                    .inSyncReplicas(partition.isr().stream().mapToInt(r -> r.id()).toArray())
                    .build());
            }

            // Get consumer groups
            var consumerGroups = getConsumerGroupsForTopic(topicName).collectList().block();

            return TopicInfo.builder()
                .name(topicName)
                .partitions(partitionInfos)
                .consumerGroups(consumerGroups)
                .configs(configs)
                .build();
        });
    }

    private Flux<ConsumerGroupInfo> getConsumerGroupsForTopic(String topicName) {
        return Mono.fromCallable(() -> 
            adminClient.listConsumerGroups()
                .all()
                .get()
                .stream()
                .map(ConsumerGroupListing::groupId)
                .collect(Collectors.toList()))
            .flatMapMany(groups -> Flux.fromIterable(groups)
                .flatMap(groupId -> getConsumerGroupInfo(groupId, topicName)));
    }

    private Mono<ConsumerGroupInfo> getConsumerGroupInfo(String groupId, String topicName) {
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

                    partitionOffsets.put(topicPartition.partition(),
                        ConsumerPartitionInfo.builder()
                            .currentOffset(offsetMetadata.offset())
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
}
