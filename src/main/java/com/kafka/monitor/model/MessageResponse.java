package com.kafka.monitor.model;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

/**
 * Response model for Kafka message data.
 */
@Data
@Builder
public class MessageResponse {
    private String clusterName;
    private String topic;
    private int partition;
    private long offset;
    private Instant timestamp;
    private String key;
    private String value;
}
