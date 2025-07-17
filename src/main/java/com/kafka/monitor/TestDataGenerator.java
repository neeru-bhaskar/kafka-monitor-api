package com.kafka.monitor;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Utility class to generate test data for Kafka Monitor API testing.
 * Creates sample topics, messages, and consumer groups.
 */
public class TestDataGenerator {
    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String ORDERS_TOPIC = "orders";
    private static final String NOTIFICATIONS_TOPIC = "notifications";
    private static final int NUM_PARTITIONS = 3;
    private static final short REPLICATION_FACTOR = 1;

    public static void main(String[] args) {
        Properties adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (AdminClient adminClient = AdminClient.create(adminProps);
             KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {

            // Create topics
            createTopics(adminClient);

            // Generate and send messages
            generateOrderMessages(producer);
            generateNotificationMessages(producer);

            System.out.println("Test data generation completed successfully!");

        } catch (Exception e) {
            System.err.println("Error generating test data: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void createTopics(AdminClient adminClient) throws ExecutionException, InterruptedException {
        List<NewTopic> topics = Arrays.asList(
            new NewTopic(ORDERS_TOPIC, NUM_PARTITIONS, REPLICATION_FACTOR),
            new NewTopic(NOTIFICATIONS_TOPIC, NUM_PARTITIONS, REPLICATION_FACTOR)
        );

        try {
            adminClient.createTopics(topics).all().get();
            System.out.println("Topics created successfully");
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof TopicExistsException)) {
                throw e;
            }
            System.out.println("Topics already exist, continuing...");
        }
    }

    private static void generateOrderMessages(KafkaProducer<String, String> producer) throws InterruptedException, ExecutionException {
        List<Future<RecordMetadata>> futures = new ArrayList<>();
        Instant now = Instant.now();

        for (int i = 0; i < 25; i++) {
            String key = "ORD-" + i;
            String value = String.format("{\"orderId\":\"%s\",\"amount\":%d,\"currency\":\"USD\",\"status\":\"PENDING\",\"customerName\":\"Customer %d\",\"items\":[{\"productId\":\"PROD-%d\",\"quantity\":%d,\"price\":%d}]}", 
                key, i * 10, i, i, 1, i * 10);
            
            ProducerRecord<String, String> record = new ProducerRecord<>(
                ORDERS_TOPIC,
                null, // Let Kafka choose the partition
                now.plusSeconds(i).toEpochMilli(),
                key,
                value
            );

            futures.add(producer.send(record));
            System.out.println("Sent order message: " + key);
        }

        // Wait for all messages to be sent
        for (Future<RecordMetadata> future : futures) {
            future.get();
        }
        System.out.println("All order messages sent successfully");
    }

    private static void generateNotificationMessages(KafkaProducer<String, String> producer) throws InterruptedException, ExecutionException {
        List<Future<RecordMetadata>> futures = new ArrayList<>();
        Instant now = Instant.now();

        String[] notificationTypes = {"ORDER_CREATED", "ORDER_SHIPPED", "ORDER_DELIVERED", "PAYMENT_RECEIVED", "ORDER_CANCELLED"};
        String[] notificationPriorities = {"HIGH", "MEDIUM", "LOW"};

        for (int i = 0; i < 15; i++) {
            String key = "NOTIF-" + i;
            String orderId = "ORD-" + (i % 5);
            String notificationType = notificationTypes[i % notificationTypes.length];
            String priority = notificationPriorities[i % notificationPriorities.length];

            String value = String.format("{\"notificationId\":\"%s\",\"type\":\"%s\",\"priority\":\"%s\",\"orderId\":\"%s\",\"message\":\"Order %s has been %s\",\"timestamp\":\"%s\"}",
                key,
                notificationType,
                priority,
                orderId,
                orderId,
                notificationType.toLowerCase().replace('_', ' '),
                now.plusSeconds(i).toString());
            
            ProducerRecord<String, String> record = new ProducerRecord<>(
                NOTIFICATIONS_TOPIC,
                null, // Let Kafka choose the partition
                now.plusSeconds(i).toEpochMilli(),
                key,
                value
            );

            futures.add(producer.send(record));
            System.out.println("Sent notification message: " + key);
        }

        // Wait for all messages to be sent
        for (Future<RecordMetadata> future : futures) {
            future.get();
        }
        System.out.println("All notification messages sent successfully");
    }
}
