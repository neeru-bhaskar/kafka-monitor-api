package com.kafka.monitor.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Manages Kafka cluster connections and clients for the application.
 * Responsible for creating and maintaining AdminClient and KafkaConsumer instances
 * for each configured Kafka cluster. These clients are used by the services
 * to interact with Kafka clusters.
 */
@RequiredArgsConstructor
public class KafkaClusterManager {
    private final KafkaProperties kafkaProperties;
    
    @Getter
    private final Map<String, AdminClient> adminClients = new HashMap<>();
    
    @Getter
    private final Map<String, KafkaConsumer<String, String>> consumers = new HashMap<>();

    @Getter
    private final Map<String, KafkaProducer<String, String>> producers = new HashMap<>();

    /**
     * Initializes AdminClient and KafkaConsumer instances for each configured cluster.
     * Called automatically after bean construction.
     * Creates clients with cluster-specific configuration and SSL settings if provided.
     */
    @PostConstruct
    public void init() {
        kafkaProperties.getClusters().forEach(cluster -> {
            // Create AdminClient
            Properties adminConfig = new Properties();
            adminConfig.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getBootstrapServers());
            if (cluster.getProperties() != null) {
                adminConfig.putAll(cluster.getProperties());
            }
            adminClients.put(cluster.getName(), AdminClient.create(adminConfig));

            // Create KafkaConsumer
            Properties consumerConfig = new Properties();
            consumerConfig.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getBootstrapServers());
            consumerConfig.put("key.deserializer", StringDeserializer.class.getName());
            consumerConfig.put("value.deserializer", StringDeserializer.class.getName());
            consumerConfig.put("group.id", "kafka-monitor-api-consumer-" + cluster.getName());
            if (cluster.getProperties() != null) {
                consumerConfig.putAll(cluster.getProperties());
            }
            consumers.put(cluster.getName(), new KafkaConsumer<>(consumerConfig));

            // Create KafkaProducer
            Properties producerConfig = new Properties();
            producerConfig.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getBootstrapServers());
            producerConfig.put("key.serializer", StringSerializer.class.getName());
            producerConfig.put("value.serializer", StringSerializer.class.getName());
            if (cluster.getProperties() != null) {
                producerConfig.putAll(cluster.getProperties());
            }
            producers.put(cluster.getName(), new KafkaProducer<>(producerConfig));
        });
    }

    /**
     * Cleans up resources by closing all AdminClient and KafkaConsumer instances.
     * Called automatically before bean destruction.
     */
    @PreDestroy
    public void cleanup() {
        adminClients.values().forEach(AdminClient::close);
        consumers.values().forEach(KafkaConsumer::close);
        producers.values().forEach(KafkaProducer::close);
    }

    /**
     * Gets the AdminClient instance for a specific cluster.
     * 
     * @param clusterName Name of the cluster to get the AdminClient for
     * @return AdminClient instance for the specified cluster
     * @throws IllegalArgumentException if cluster name is not found
     */
    public AdminClient getAdminClient(String clusterName) {
        AdminClient client = adminClients.get(clusterName);
        if (client == null) {
            throw new IllegalArgumentException("Unknown Kafka cluster: " + clusterName);
        }
        return client;
    }

    /**
     * Gets the KafkaConsumer instance for a specific cluster.
     * 
     * @param clusterName Name of the cluster to get the KafkaConsumer for
     * @return KafkaConsumer instance for the specified cluster
     * @throws IllegalArgumentException if cluster name is not found
     */
    public KafkaConsumer<String, String> getConsumer(String clusterName) {
        KafkaConsumer<String, String> consumer = consumers.get(clusterName);
        if (consumer == null) {
            throw new IllegalArgumentException("Unknown Kafka cluster: " + clusterName);
        }
        return consumer;
    }

    /**
     * Gets a map of all configured clusters and their bootstrap servers.
     * 
     * @return Map where key is cluster name and value is bootstrap servers string
     */
    /**
     * Gets the KafkaProducer instance for a specific cluster.
     * 
     * @param clusterName Name of the cluster to get the KafkaProducer for
     * @return KafkaProducer instance for the specified cluster
     * @throws IllegalArgumentException if cluster name is not found
     */
    public KafkaProducer<String, String> getProducer(String clusterName) {
        KafkaProducer<String, String> producer = producers.get(clusterName);
        if (producer == null) {
            throw new IllegalArgumentException("Unknown Kafka cluster: " + clusterName);
        }
        return producer;
    }

    /**
     * Gets a map of all configured clusters and their bootstrap servers.
     * 
     * @return Map where key is cluster name and value is bootstrap servers string
     */
    public Map<String, String> getClusters() {
        Map<String, String> clusterInfo = new HashMap<>();
        kafkaProperties.getClusters().forEach(cluster -> 
            clusterInfo.put(cluster.getName(), cluster.getBootstrapServers())
        );
        return clusterInfo;
    }
}
