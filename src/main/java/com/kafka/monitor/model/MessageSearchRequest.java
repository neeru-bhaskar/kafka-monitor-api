package com.kafka.monitor.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.Instant;

/**
 * Request model for searching Kafka messages.
 * Supports searching by topic, partition, timestamp range, and offset range.
 * At least one of the following combinations must be provided:
 * - startOffset and endOffset
 * - startTimestamp and endTimestamp
 * If partition is not specified, search will be performed across all partitions.
 */
@Data
public class MessageSearchRequest {
    /** Name of the topic to search in */
    @NotBlank(message = "Topic name is required")
    private String topic;

    /** Optional partition number to limit search to. If null, searches all partitions */
    private Integer partition;

    /** Start offset (inclusive) for range-based search */
    private Long startOffset;

    /** End offset (exclusive) for range-based search */
    private Long endOffset;

    /** Start timestamp (inclusive) for time-based search */
    private Instant startTimestamp;

    /** End timestamp (exclusive) for time-based search */
    private Instant endTimestamp;
}
