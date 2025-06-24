package com.kafka.monitor.controller;

import com.kafka.monitor.model.*;
import org.springframework.http.MediaType;
import com.kafka.monitor.service.KafkaMonitorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.errors.UnknownMemberIdException;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * REST controller for Kafka monitoring operations.
 * Provides endpoints for managing multiple Kafka clusters, topics, and consumer groups.
 * All endpoints return application/json responses.
 */
@RestController
@RequestMapping("/api/kafka")
@RequiredArgsConstructor
@CrossOrigin
public class KafkaMonitorController {
    private final KafkaMonitorService kafkaMonitorService;

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return Mono.just(Map.of("error", ex.getMessage()));
    }

    /**
     * List all configured Kafka clusters.
     * 
     * @return Flux of cluster name and bootstrap server pairs
     */
    @GetMapping(value = "/clusters", produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<ClusterInfo> listClusters() {
        return kafkaMonitorService.listClusters()
            .map(entry -> new ClusterInfo(entry.getKey(), entry.getValue()));
    }

    /**
     * List all topics in a specified Kafka cluster.
     * 
     * @param clusterName name of the Kafka cluster
     * @return Mono containing list of topic names
     */
    @GetMapping(value = "/clusters/{clusterName}/topics", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<TopicsList> listTopics(@PathVariable String clusterName) {
        return kafkaMonitorService.listTopics(clusterName)
                .collectList()
                .map(TopicsList::new);
//            .map(topic -> new TopicListResponse(topic));

    }

    /**
     * Get detailed information about a specific topic.
     * 
     * @param clusterName name of the Kafka cluster
     * @param topicName name of the topic
     * @return Mono containing topic details including partitions and offsets
     */
    @GetMapping(value = "/clusters/{clusterName}/topics/{topicName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<TopicInfo> getTopicInfo(
            @PathVariable String clusterName,
            @PathVariable String topicName) {
        return kafkaMonitorService.getTopicInfo(clusterName, topicName);
    }

    /**
     * Update consumer group offset for a specific topic partition.
     * 
     * @param clusterName name of the Kafka cluster
     * @param groupId consumer group ID
     * @param request offset update details including topic, partition, and new offset
     * @return Mono<Void> completing when offset is updated
     * @throws IllegalArgumentException if offset is invalid or out of range
     */
    @PostMapping(value = "/clusters/{clusterName}/consumer-groups/{groupId}/offsets", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Void> updateConsumerGroupOffset(
            @PathVariable String clusterName,
            @PathVariable String groupId,
            @Valid @RequestBody OffsetUpdateRequest request) {
        return kafkaMonitorService.updateConsumerGroupOffset(
                clusterName,
                groupId,
                request.getTopic(),
                request.getPartition(),
                request.getOffset())
            .onErrorMap(e -> e instanceof ExecutionException && e.getCause() instanceof UnknownMemberIdException,
                       e -> new IllegalStateException("Consumer group '" + groupId + "' does not exist"));
    }
}
