# Kafka Monitor API Testing Guide

This document provides examples of how to test all the Kafka Monitor API endpoints using `curl` commands. All examples assume you're running the application locally on port 8080.

## Prerequisites

1. The application is running (`./gradlew bootRun`)
2. A local Kafka broker is running at `localhost:9092`
3. Test data has been generated (`./gradlew generateTestData`)
4. `curl` and `jq` are installed for making requests and formatting JSON responses

## Available Endpoints

### 1. List Available Clusters

```bash
curl -s http://localhost:8080/api/kafka/clusters | jq
```

Expected response:
```json
[
  {
    "name": "local",
    "bootstrapServers": "localhost:9092"
  }
]
```

### 2. List Topics in a Cluster

```bash
curl -s http://localhost:8080/api/kafka/clusters/local/topics | jq
```

Expected response:
```json
{
  "topics": [
    "orders",
    "notifications"
  ]
}
```

### 3. Get Topic Details

```bash
curl -s http://localhost:8080/api/kafka/clusters/local/topics/orders | jq
```

This shows detailed information about the topic, including:
- Partitions and their offsets
- Consumer groups and their offsets
- Topic configuration

### 4. Message Retrieval

#### 4.1 Get Single Message by Offset

```bash
# Get message at offset 0 from partition 0
curl -s http://localhost:8080/api/kafka/clusters/local/topics/orders/partitions/0/offsets/0 | jq
```

Expected response:
```json
{
  "clusterName": "local",
  "topic": "orders",
  "partition": 0,
  "offset": 0,
  "timestamp": 1750349703.908000000,
  "key": "ORD-8",
  "value": "Order 8 for $80"
}
```

#### 4.2 Search Messages

##### Search by Offset Range
```bash
curl -s -X POST http://localhost:8080/api/kafka/clusters/local/messages/search \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "orders",
    "partition": 0,
    "startOffset": 0,
    "endOffset": 5
  }' | jq
```

##### Search Across All Partitions
```bash
curl -s -X POST http://localhost:8080/api/kafka/clusters/local/messages/search \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "orders",
    "startOffset": 0,
    "endOffset": 5
  }' | jq
```

##### Search by Timestamp Range
```bash
curl -s -X POST http://localhost:8080/api/kafka/clusters/local/messages/search \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "orders",
    "startTimestamp": "2025-06-25T15:15:02.970Z",
    "endTimestamp": "2025-06-25T15:15:03.908Z"
  }' | jq
```

##### Search by Text Content (Key or Value)
```bash
curl -s -X POST http://localhost:8080/api/kafka/clusters/local/messages/text-search \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "orders",
    "searchText": "Order 8"
  }' | jq
```

This will return all messages where either the key or value contains the text "Order 8" (case-insensitive). The search is performed across all partitions of the topic, and returns up to 50 matching messages.

### 5. Error Cases

#### 5.1 Invalid Partition

```bash
curl -s http://localhost:8080/api/kafka/clusters/local/topics/orders/partitions/999/offsets/0 | jq
```

Expected response:
```json
{
  "error": "Invalid Request",
  "message": "Invalid partition number 999 for topic 'orders'. Valid range: 0-2",
  "status": 400
}
```

#### 5.2 Invalid Offset

```bash
curl -s http://localhost:8080/api/kafka/clusters/local/topics/orders/partitions/0/offsets/999 | jq
```

Expected response:
```json
{
  "error": "Invalid Request",
  "message": "Invalid offset 999 for topic 'orders' partition 0. Valid range: 0-25",
  "status": 400
}
```

#### 5.3 Non-existent Topic

```bash
curl -s http://localhost:8080/api/kafka/clusters/local/topics/nonexistent/partitions/0/offsets/0 | jq
```

Expected response:
```json
{
  "error": "Invalid Request",
  "message": "Topic 'nonexistent' does not exist",
  "status": 400
}
```

## Tips

1. Use `jq` for pretty-printing JSON responses: pipe the curl output through `| jq`
2. For POST requests, remember to set the `Content-Type: application/json` header
3. All error responses follow the same format with `error`, `message`, and `status` fields
4. The message search endpoint returns up to 50 messages at a time
5. When searching without a partition specified, the API will search across all partitions
