package com.kafka.monitor.model;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

/**
 * Response model for Kafka message data.
 * Contains all metadata and content for a single Kafka message.
 */
@Data
@Builder
public class MessageResponse {
    /** Name of the Kafka cluster this message was retrieved from */
    private String clusterName;
    
    /** Name of the topic this message belongs to */
    private String topic;
    
    /** Partition number within the topic */
    private int partition;
    
    /** Message offset within the partition */
    private long offset;
    
    /** Timestamp when the message was written to Kafka */
    private Instant timestamp;
    
    /** Message key (may be null) */
    private String key;
    
    /** Message value/content */
    private String value;
}
