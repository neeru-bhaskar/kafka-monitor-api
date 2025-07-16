package com.kafka.monitor.config;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * Root configuration class for Kafka cluster properties.
 * Maps to the 'kafka' section in application.yml.
 * Example configuration:
 * kafka:
 *   clusters:
 *     - name: local
 *       bootstrapServers: localhost:9092
 *     - name: prod
 *       bootstrapServers: kafka1:9092,kafka2:9092
 *       properties:
 *         security.protocol: SSL
 */
@Data
public class KafkaProperties {
    /** List of Kafka clusters available to the application.
     * Each cluster has its own configuration properties. */
    private List<KafkaClusterProperties> clusters = new ArrayList<>();
}
