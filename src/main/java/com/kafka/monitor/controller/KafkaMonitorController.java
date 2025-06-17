package com.kafka.monitor.controller;

import com.kafka.monitor.model.TopicInfo;
import com.kafka.monitor.service.KafkaMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
}
