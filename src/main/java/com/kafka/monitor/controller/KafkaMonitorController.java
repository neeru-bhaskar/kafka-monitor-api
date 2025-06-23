package com.kafka.monitor.controller;

import com.kafka.monitor.model.TopicInfo;
import com.kafka.monitor.model.OffsetUpdateRequest;
import com.kafka.monitor.service.KafkaMonitorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.errors.UnknownMemberIdException;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/kafka")
@RequiredArgsConstructor
@CrossOrigin
public class KafkaMonitorController {
    private final KafkaMonitorService kafkaMonitorService;

    @GetMapping("/clusters")
    public Flux<Map.Entry<String, String>> listClusters() {
        return kafkaMonitorService.listClusters();
    }

    @GetMapping("/clusters/{clusterName}/topics")
    public Flux<String> listTopics(@PathVariable String clusterName) {
        return kafkaMonitorService.listTopics(clusterName);
    }

    @GetMapping("/clusters/{clusterName}/topics/{topicName}")
    public Mono<TopicInfo> getTopicInfo(
            @PathVariable String clusterName,
            @PathVariable String topicName) {
        return kafkaMonitorService.getTopicInfo(clusterName, topicName);
    }

    @PostMapping("/clusters/{clusterName}/consumer-groups/{groupId}/offsets")
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
