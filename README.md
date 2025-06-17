# Kafka Monitor API

A reactive Spring Boot application that provides REST APIs to monitor Kafka clusters, topics, and consumer groups.

## Features

- List Kafka clusters
- List topics in a Kafka cluster
- Get detailed topic information including:
  - Partition details
  - Consumer group information
  - Offset and lag metrics
  - Topic configurations

## Prerequisites

- Java 17 or higher
- Gradle
- Apache Kafka (running on localhost:9092 by default)

## Building the Application

```bash
./gradlew clean build
```

## Running the Application

```bash
./gradlew bootRun
```

The application will start on `http://localhost:8080`

## API Endpoints

1. List Kafka Clusters
   ```
   GET /api/kafka/clusters
   ```

2. List Topics
   ```
   GET /api/kafka/topics
   ```

3. Get Topic Details
   ```
   GET /api/kafka/topics/{topicName}
   ```

## Configuration

The default Kafka broker address is set to `localhost:9092`. To change this, modify the `bootstrap.servers` property in `KafkaConfig.java`.
