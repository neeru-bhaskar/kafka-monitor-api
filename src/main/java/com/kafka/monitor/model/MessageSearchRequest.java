package com.kafka.monitor.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.Instant;

/**
 * Request model for searching Kafka messages.
 * Supports searching by topic, partition, timestamp range, and offset range.
 */
@Data
public class MessageSearchRequest {
    @NotBlank(message = "Topic name is required")
    private String topic;
    private Integer partition;
    private Long startOffset;
    private Long endOffset;
    private Instant startTimestamp;
    private Instant endTimestamp;
}
