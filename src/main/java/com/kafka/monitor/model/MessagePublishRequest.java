package com.kafka.monitor.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MessagePublishRequest {
    @NotBlank(message = "Topic name is required")
    private String topic;

    @NotNull(message = "Message key is required")
    private String key;

    @NotNull(message = "Message value is required")
    private String value;

    private Integer partition;  // Optional, if not specified, Kafka will choose a partition
}
