package com.kafka.monitor.model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.Instant;

/**
 * Request model for searching Kafka messages.
 * Supports searching by topic, partition, timestamp range, offset range, and text content.
 * At least one of the following search parameters must be provided:
 * - partition
 * - startOffset
 * - endOffset
 * - startTimestamp
 * - endTimestamp
 * - searchText
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

    /** Optional text to search for in message keys and values (case-insensitive) */
    private String searchText;

    /**
     * Validates that at least one search parameter is provided.
     * @return true if at least one search parameter is provided, false otherwise
     */
    @AssertTrue(message = "At least one search parameter (partition, startOffset, endOffset, startTimestamp, endTimestamp, or searchText) must be provided")
    public boolean isAtLeastOneSearchParameterProvided() {
        return partition != null ||
               startOffset != null ||
               endOffset != null ||
               startTimestamp != null ||
               endTimestamp != null ||
               (searchText != null && !searchText.trim().isEmpty());
    }
}
