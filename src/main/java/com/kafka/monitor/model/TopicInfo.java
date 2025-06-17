package com.kafka.monitor.model;

import lombok.Data;
import lombok.Builder;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class TopicInfo {
    private String name;
    private List<PartitionInfo> partitions;
    private List<ConsumerGroupInfo> consumerGroups;
    private Map<String, String> configs;
}
