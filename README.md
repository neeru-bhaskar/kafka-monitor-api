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
   Returns a list of available Kafka clusters and their bootstrap servers.

2. List Topics in a Cluster
   ```
   GET /api/kafka/clusters/{clusterName}/topics
   ```
   Lists all topics in the specified Kafka cluster.

3. Get Topic Details
   ```
   GET /api/kafka/clusters/{clusterName}/topics/{topicName}
   ```
   Returns detailed information about a topic including:
   - Partition information (leader, replicas, in-sync replicas)
   - Beginning and end offsets
   - Consumer group offsets and lag
   - Topic configurations

4. Update Consumer Group Offset
   ```
   POST /api/kafka/clusters/{clusterName}/consumer-groups/{groupId}/offsets
   Content-Type: application/json

   {
     "topic": "topic-name",
     "partition": 0,
     "offset": 100
   }
   ```
   Updates the offset for a consumer group on a specific topic partition.
   - Returns 400 Bad Request if offset is invalid
   - Returns 500 Internal Server Error if consumer group doesn't exist

## Configuration

The default Kafka broker address is set to `localhost:9092`. To change this, modify the `bootstrap.servers` property in `KafkaConfig.java`.
