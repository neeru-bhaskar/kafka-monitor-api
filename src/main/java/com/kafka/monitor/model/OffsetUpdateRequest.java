package com.kafka.monitor.model;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class OffsetUpdateRequest {
    @NotBlank(message = "Topic name is required")
    private String topic;

    @Min(value = 0, message = "Partition must be non-negative")
    private int partition;

    @NotNull(message = "Offset is required")
    @Min(value = 0, message = "Offset must be non-negative")
    private long offset;
}
