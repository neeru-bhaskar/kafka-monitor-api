# Kafka Monitor API - Postman Collection

This directory contains a Postman collection for testing the Kafka Monitor API endpoints.

## Prerequisites

1. Install [Postman](https://www.postman.com/downloads/)
2. Ensure the Kafka Monitor API is running locally on port 8080
3. Have a local Kafka broker running at localhost:9092
4. Generate test data using `./gradlew generateTestData`

## Importing the Collection

1. Open Postman
2. Click on "Import" in the top left
3. Drag and drop the `kafka-monitor-api.postman_collection.json` file or click "Upload Files" to select it
4. Click "Import" to add the collection to your workspace

## Collection Structure

The collection is organized into the following folders:

1. **Clusters**
   - List Clusters

2. **Topics**
   - List Topics
   - Get Topic Info

3. **Messages**
   - Get Message by Offset
   - Search Messages - By Offset Range
   - Search Messages - By Time Range
   - Search Messages - By Text
   - Search Messages - Combined Criteria

4. **Consumer Groups**
   - Update Consumer Group Offset

## Using the Collection

1. All requests are configured for `localhost:8080` by default
2. The `local` cluster name is used in all requests
3. Sample request bodies are provided for POST requests
4. For message search requests, you can modify the search criteria in the request body as needed

## Common Variables

- Host: `localhost`
- Port: `8080`
- Base Path: `/api/kafka`
- Cluster Name: `local`
- Example Topic: `orders`

## Response Examples

Each request in the collection will return JSON responses as documented in `TESTING.md` and `MESSAGE_SEARCH.md`.
