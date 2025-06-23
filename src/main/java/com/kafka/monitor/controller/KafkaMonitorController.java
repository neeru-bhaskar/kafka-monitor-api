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

import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/kafka")
@RequiredArgsConstructor
@CrossOrigin
public class KafkaMonitorController {
    private final KafkaMonitorService kafkaMonitorService;

    @GetMapping("/clusters")
    public Flux<String> listClusters() {
        return kafkaMonitorService.listClusters();
    }

    @GetMapping("/topics")
    public Flux<String> listTopics() {
        return kafkaMonitorService.listTopics();
    }

    @GetMapping("/topics/{topicName}")
    public Mono<TopicInfo> getTopicInfo(@PathVariable String topicName) {
        return kafkaMonitorService.getTopicInfo(topicName);
    }

    @PostMapping("/consumer-groups/{groupId}/offsets")
    public Mono<Void> updateConsumerGroupOffset(
            @PathVariable String groupId,
            @Valid @RequestBody OffsetUpdateRequest request) {
        return kafkaMonitorService.updateConsumerGroupOffset(
            groupId,
            request.getTopic(),
            request.getPartition(),
            request.getOffset()
        )
        .onErrorMap(ExecutionException.class, e -> {
            if (e.getCause() instanceof UnknownMemberIdException) {
                return new IllegalStateException("Consumer group " + groupId + " does not exist");
            }
            return e;
        });
    }
}
