package com.kafka.monitor;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.*;
import java.util.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class TestDataGenerator {
    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String ORDERS_TOPIC = "orders";
    private static final String NOTIFICATIONS_TOPIC = "notifications";
    
    public static void main(String[] args) throws Exception {
        // Create topics
        createTopics();
        
        // Start consumers in separate threads
        startConsumer("orders-group", ORDERS_TOPIC);
        startConsumer("notifications-group", NOTIFICATIONS_TOPIC);
        
        // Produce messages
        produceMessages();
    }
    
    private static void createTopics() throws Exception {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000");
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "5000");
        
        try (AdminClient admin = AdminClient.create(props)) {
            // First check if Kafka is available
            try {
                admin.listTopics().names().get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                System.err.println("Error: Cannot connect to Kafka at " + BOOTSTRAP_SERVERS);
                System.err.println("Please make sure Kafka is running and try again.");
                System.exit(1);
            }

            // Check if topics already exist
            Set<String> existingTopics = admin.listTopics().names().get(5, TimeUnit.SECONDS);
            List<NewTopic> topicsToCreate = new ArrayList<>();

            if (!existingTopics.contains(ORDERS_TOPIC)) {
                topicsToCreate.add(new NewTopic(ORDERS_TOPIC, 3, (short) 1));
            }
            if (!existingTopics.contains(NOTIFICATIONS_TOPIC)) {
                topicsToCreate.add(new NewTopic(NOTIFICATIONS_TOPIC, 2, (short) 1));
            }

            if (!topicsToCreate.isEmpty()) {
                admin.createTopics(topicsToCreate).all().get(5, TimeUnit.SECONDS);
                System.out.println("Topics created successfully: " + 
                    topicsToCreate.stream().map(NewTopic::name).collect(Collectors.joining(", ")));
            } else {
                System.out.println("Topics already exist");
            }
        }
    }
    
    private static void produceMessages() throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            // Produce messages to orders topic
            for (int i = 0; i < 100; i++) {
                String orderId = "ORD-" + i;
                String orderValue = String.format("Order %d for $%d", i, (i * 10));
                producer.send(new ProducerRecord<>(ORDERS_TOPIC, orderId, orderValue));
                
                if (i % 3 == 0) {
                    // Send notification for every third order
                    String notificationMsg = String.format("Notification for order %d", i);
                    producer.send(new ProducerRecord<>(NOTIFICATIONS_TOPIC, orderId, notificationMsg));
                }
                
                Thread.sleep(100); // Simulate some delay
            }
            System.out.println("Finished producing messages");
        }
    }
    
    private static void startConsumer(String groupId, String topic) {
        Thread consumerThread = new Thread(() -> {
            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            
            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
                consumer.subscribe(Collections.singletonList(topic));
                
                // Consume only half of the messages to demonstrate lag
                int messageCount = 0;
                boolean running = true;
                
                while (running) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                    for (ConsumerRecord<String, String> record : records) {
                        System.out.printf("Consumer %s: Received message: key=%s, value=%s%n",
                            groupId, record.key(), record.value());
                        messageCount++;
                        
                        // Stop after consuming half of the messages
                        if (messageCount >= 50) {
                            running = false;
                            break;
                        }
                    }
                }
            }
            System.out.printf("Consumer %s finished%n", groupId);
        });
        
        consumerThread.start();
    }
}
