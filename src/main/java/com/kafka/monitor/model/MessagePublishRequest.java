package com.kafka.monitor.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MessagePublishRequest {
    @NotBlank(message = "Topic name is required")
    private String topic;

    private String key;

    @NotNull(message = "Message value is required")
    private JsonNode value;

    private Integer partition;  // Optional, if not specified, Kafka will choose a partition
}
