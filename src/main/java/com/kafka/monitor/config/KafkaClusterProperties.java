package com.kafka.monitor.config;

import lombok.Data;
import java.util.Map;

@Data
public class KafkaClusterProperties {
    private String name;
    private String bootstrapServers;
    private Map<String, String> properties;
}
