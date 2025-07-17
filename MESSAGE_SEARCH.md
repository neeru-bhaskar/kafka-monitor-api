# Kafka Monitor API - Message Search Guide

This document provides detailed examples of using the message search endpoint (`/api/kafka/clusters/{clusterName}/messages/search`). The endpoint supports various search criteria and requires at least one search parameter to be provided.

## Search Parameters

The search endpoint accepts the following parameters:
- `topic` (required): Name of the topic to search in
- `partition` (optional): Specific partition to search in
- `startOffset` (optional): Starting offset (inclusive)
- `endOffset` (optional): Ending offset (exclusive)
- `startTimestamp` (optional): Starting timestamp (inclusive, ISO-8601 format)
- `endTimestamp` (optional): Ending timestamp (exclusive, ISO-8601 format)
- `searchText` (optional): Text to search for in message keys and values (case-insensitive)

At least one of the optional parameters must be provided.

## Search Scenarios

### 1. Invalid Search (No Parameters)

Request:
```bash
curl -s -X POST http://localhost:8080/api/kafka/clusters/local/messages/search \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "orders"
  }' | jq
```

Response (400 Bad Request):
```json
{
  "error": "Validation Error",
  "errors": [
    "atLeastOneSearchParameterProvided: At least one search parameter (partition, startOffset, endOffset, startTimestamp, endTimestamp, or searchText) must be provided"
  ],
  "status": 400
}
```

### 2. Search by Partition and Offset Range

Request:
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

Response:
```json
[
  {
    "clusterName": "local",
    "topic": "orders",
    "partition": 0,
    "offset": 0,
    "timestamp": "2025-07-16T22:00:00Z",
    "key": "ORD-0",
    "value": {
      "orderId": "ORD-0",
      "amount": 0,
      "currency": "USD",
      "status": "PENDING",
      "customerName": "Customer 0",
      "items": [
        {
          "productId": "PROD-0",
          "quantity": 1,
          "price": 0
        }
      ]
    }
  }
  // ... more messages
]
```

### 3. Search by Time Range

Request:
```bash
curl -s -X POST http://localhost:8080/api/kafka/clusters/local/messages/search \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "orders",
    "startTimestamp": "2025-07-16T22:00:00Z",
    "endTimestamp": "2025-07-16T23:00:00Z"
  }' | jq
```

Response will include all messages with timestamps within the specified range.

### 4. Search by Text Content

Request:
```bash
curl -s -X POST http://localhost:8080/api/kafka/clusters/local/messages/search \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "orders",
    "searchText": "Customer 1"
  }' | jq
```

Response will include all messages where either the key or value contains "Customer 1" (case-insensitive).

### 5. Combined Search (Multiple Criteria)

Request:
```bash
curl -s -X POST http://localhost:8080/api/kafka/clusters/local/messages/search \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "orders",
    "partition": 1,
    "startOffset": 18,
    "endOffset": 25,
    "searchText": "Customer 1"
  }' | jq
```

Response will include messages that match ALL specified criteria:
- In partition 1
- Between offsets 18 and 25
- Containing "Customer 1" in key or value

## Notes

1. The endpoint returns up to 50 messages that match the search criteria
2. Text search is case-insensitive and matches against both message keys and values
3. All timestamps must be in ISO-8601 format with timezone (e.g., "2025-07-16T22:00:00Z")
4. If a partition is not specified, the search will be performed across all partitions
5. Messages are returned in order of offset within each partition
6. If parsing a message value as JSON fails, that message is skipped and not included in results

## Publishing Messages

You can publish new messages to a topic using the publish endpoint.

Request:
```bash
curl -s -X POST http://localhost:8080/api/kafka/clusters/local/messages \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "orders",
    "key": "ORD-100",
    "value": {
      "orderId": "ORD-100",
      "amount": 1000,
      "currency": "USD",
      "status": "PENDING",
      "customerName": "Customer 100",
      "items": [{
        "productId": "PROD-100",
        "quantity": 1,
        "price": 1000
      }]
    },
    "partition": 0
  }' | jq
```

Parameters:
- `topic` (required): Name of the topic to publish to
- `key` (required): Message key
- `value` (required): Message value as a JSON object
- `partition` (optional): Specific partition to publish to. If not specified, Kafka will choose based on the key

Response:
The endpoint returns the offset where the message was written:
```json
42
```

Notes:
1. The message value must be a valid JSON object
2. If a partition is specified, it must be valid for the topic
3. If no partition is specified, Kafka will choose one based on the key's hash
