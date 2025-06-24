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

@RestController
@RequestMapping("/api/kafka")
@RequiredArgsConstructor
@CrossOrigin
public class KafkaMonitorController {
    private final KafkaMonitorService kafkaMonitorService;

    @GetMapping(value = "/clusters", produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<ClusterInfo> listClusters() {
        return kafkaMonitorService.listClusters()
            .map(entry -> new ClusterInfo(entry.getKey(), entry.getValue()));
    }

    @GetMapping(value = "/clusters/{clusterName}/topics", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<TopicsList> listTopics(@PathVariable String clusterName) {
        return kafkaMonitorService.listTopics(clusterName)
                .collectList()
                .map(TopicsList::new);
//            .map(topic -> new TopicListResponse(topic));

    }

    @GetMapping(value = "/clusters/{clusterName}/topics/{topicName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<TopicInfo> getTopicInfo(
            @PathVariable String clusterName,
            @PathVariable String topicName) {
        return kafkaMonitorService.getTopicInfo(clusterName, topicName);
    }

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
