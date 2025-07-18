package com.kafka.monitor.controller;

import com.kafka.monitor.model.MessagePublishRequest;
import com.kafka.monitor.model.MessageResponse;
import com.kafka.monitor.model.MessageSearchRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.kafka.monitor.service.KafkaMessageService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controller for Kafka message retrieval operations.
 * Provides endpoints to retrieve individual messages and search through messages.
 */
@RestController
@RequestMapping("/api/kafka")
@CrossOrigin
public class KafkaMessageController {
    private final KafkaMessageService kafkaMessageService;

    public KafkaMessageController(KafkaMessageService kafkaMessageService) {
        this.kafkaMessageService = kafkaMessageService;
    }

    /**
     * Retrieves a message at a specific offset from a topic partition.
     *
     * @param clusterName Name of the Kafka cluster
     * @param topic Topic name
     * @param partition Partition number
     * @param offset Message offset
     * @return Message data if found
     */
    @GetMapping(value = "/clusters/{clusterName}/topics/{topic}/partitions/{partition}/offsets/{offset}", 
               produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<MessageResponse> getMessage(
            @PathVariable String clusterName,
            @PathVariable String topic,
            @PathVariable int partition,
            @PathVariable long offset) {
        return kafkaMessageService.getMessage(clusterName, topic, partition, offset);
    }

    /**
     * Searches for messages in a topic based on various criteria.
     * Returns up to 50 messages matching the criteria.
     * At least one search parameter must be provided:
     * - partition
     * - startOffset
     * - endOffset
     * - startTimestamp
     * - endTimestamp
     * - searchText
     *
     * @param clusterName Name of the Kafka cluster
     * @param request Search criteria including topic and at least one search parameter
     * @return Stream of messages matching the criteria
     */
    @PostMapping(value = "/clusters/{clusterName}/messages/search",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<MessageResponse> searchMessages(
            @PathVariable String clusterName,
            @Valid @RequestBody MessageSearchRequest request) {
        return kafkaMessageService.searchMessages(clusterName, request);
    }

    /**
     * Publishes a message to a specific topic and partition.
     * If partition is not specified, Kafka will choose one based on the key.
     *
     * @param clusterName Name of the Kafka cluster
     * @param request Message publish request containing topic, key, value, and optional partition
     * @return Offset where the message was written
     */
    @PostMapping(value = "/clusters/{clusterName}/messages",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Long> publishMessage(
            @PathVariable String clusterName,
            @RequestBody MessagePublishRequest request) {
        return kafkaMessageService.publishMessage(
            clusterName,
            request.getTopic(),
            request.getKey(),
            request.getValue(),
            request.getPartition());
    }
}
