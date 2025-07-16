package com.kafka.monitor.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request model for searching Kafka messages by text content.
 * Supports searching in both message keys and values.
 * The search is case-insensitive and will match any messages where either
 * the key or value contains the search text as a substring.
 * Results are limited to 50 messages maximum.
 */
@Data
public class MessageTextSearchRequest {
    /** Name of the topic to search in */
    @NotBlank(message = "Topic name is required")
    private String topic;
    
    /** Text to search for in message keys and values (case-insensitive) */
    @NotBlank(message = "Search text is required")
    private String searchText;
}
