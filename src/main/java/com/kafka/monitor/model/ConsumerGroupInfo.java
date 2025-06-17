package com.kafka.monitor.model;

import lombok.Data;
import lombok.Builder;
import java.util.Map;

@Data
@Builder
public class ConsumerGroupInfo {
    private String groupId;
    private Map<Integer, ConsumerPartitionInfo> partitionOffsets;
    private String state;
}
