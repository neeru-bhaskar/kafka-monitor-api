package com.kafka.monitor.controller;

import com.kafka.monitor.model.*;
import com.kafka.monitor.service.KafkaMonitorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.http.MediaType;

import java.util.Map;

@ExtendWith(MockitoExtension.class)
class KafkaMonitorControllerTest {

    @Mock
    private KafkaMonitorService kafkaMonitorService;

    private WebTestClient webTestClient;
    private KafkaMonitorController controller;

    /**
     * Set up test environment before each test.
     * Initializes WebTestClient and controller with mocked service.
     */
    @BeforeEach
    void setUp() {
        controller = new KafkaMonitorController(kafkaMonitorService);
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    /**
     * Test that GET /clusters endpoint returns a list of Kafka clusters.
     * Verifies response status and content type.
     */
    @Test
    void listClusters_ShouldReturnClusterList() {
        // Given
        var clusterEntry = new AbstractMap.SimpleEntry<>("local", "localhost:9092");
        when(kafkaMonitorService.listClusters()).thenReturn(Flux.just(clusterEntry));

        // When & Then
        webTestClient.get()
                .uri("/api/kafka/clusters")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$[0].name").isEqualTo("local")
                .jsonPath("$[0].bootstrapServers").isEqualTo("localhost:9092");
    }

    /**
     * Test that GET /clusters/{clusterName}/topics endpoint returns a list of topics.
     * Verifies response status and content type.
     */
    @Test
    void listTopics_ShouldReturnTopicsList() {
        // Given
        List<String> topics = Arrays.asList("orders", "notifications");
        when(kafkaMonitorService.listTopics(eq("local")))
                .thenReturn(Flux.fromIterable(topics));

        // When & Then
        webTestClient.get()
                .uri("/api/kafka/clusters/local/topics")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$.topics[0]").isEqualTo("orders")
                .jsonPath("$.topics[1]").isEqualTo("notifications");
    }

    /**
     * Test that GET /clusters/{clusterName}/topics/{topicName} endpoint returns topic details.
     * Verifies response status, content type, and topic information.
     */
    @Test
    void getTopicInfo_ShouldReturnTopicDetails() {
        // Given
        TopicInfo topicInfo = TopicInfo.builder()
            .name("orders")
            .partitions(List.of())
            .consumerGroups(List.of())
            .configs(Map.of())
            .build();
        when(kafkaMonitorService.getTopicInfo(eq("local"), eq("orders")))
                .thenReturn(Mono.just(topicInfo));

        // When & Then
        webTestClient.get()
                .uri("/api/kafka/clusters/local/topics/orders")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$.name").isEqualTo("orders");
    }

    /**
     * Test that POST /clusters/{clusterName}/consumer-groups/{groupId}/offsets endpoint updates offsets.
     * Verifies successful offset update with 200 OK response.
     */
    @Test
    void updateConsumerGroupOffset_ShouldUpdateOffset() {
        // Given
        String clusterName = "local";
        String groupId = "orders-group";
        OffsetUpdateRequest request = new OffsetUpdateRequest();
        request.setTopic("orders");
        request.setPartition(0);
        request.setOffset(100L);

        when(kafkaMonitorService.updateConsumerGroupOffset(
                eq(clusterName),
                eq(groupId),
                eq(request.getTopic()),
                eq(request.getPartition()),
                eq(request.getOffset())))
                .thenReturn(Mono.empty());

        // When & Then
        webTestClient.post()
                .uri("/api/kafka/clusters/{clusterName}/consumer-groups/{groupId}/offsets", clusterName, groupId)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * Test that POST /clusters/{clusterName}/consumer-groups/{groupId}/offsets endpoint
     * returns 400 Bad Request for invalid offset value.
     * Verifies error response and message content.
     */
    @Test
    void updateConsumerGroupOffset_WithInvalidOffset_ShouldReturnBadRequest() {
        // Given
        String clusterName = "local";
        String groupId = "test-group";
        OffsetUpdateRequest request = new OffsetUpdateRequest();
        request.setTopic("test-topic");
        request.setPartition(0);
        request.setOffset(-1L);

        when(kafkaMonitorService.updateConsumerGroupOffset(
                eq(clusterName),
                eq(groupId),
                eq(request.getTopic()),
                eq(request.getPartition()),
                eq(request.getOffset())))
                .thenReturn(Mono.error(new IllegalArgumentException("Offset -1 is out of range")));

        // When & Then
        webTestClient.post()
                .uri("/api/kafka/clusters/{clusterName}/consumer-groups/{groupId}/offsets", clusterName, groupId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .value(response -> assertThat(response.get("error")).isEqualTo("Offset -1 is out of range"));
    }
}
