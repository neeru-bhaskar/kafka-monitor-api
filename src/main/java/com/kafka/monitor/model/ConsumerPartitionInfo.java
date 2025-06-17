package com.kafka.monitor.model;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class ConsumerPartitionInfo {
    private long currentOffset;
    private long endOffset;
    private long lag;
    private String consumerId;
    private String clientId;
    private String host;
}
