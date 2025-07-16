package com.kafka.monitor.config;

import lombok.Data;
import java.util.Map;

/**
 * Configuration properties for a single Kafka cluster.
 * Used to configure connection details and additional properties for each cluster
 * defined in application.yml.
 */
@Data
public class KafkaClusterProperties {
    /** Unique name/identifier for the cluster */
    private String name;

    /** Bootstrap servers list in host:port format, comma-separated for multiple brokers */
    private String bootstrapServers;

    /** Additional Kafka client properties specific to this cluster (optional)
     * Examples include security settings, timeouts, etc. */
    private Map<String, String> properties;
}
