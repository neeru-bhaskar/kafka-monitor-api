#!/bin/bash
./gradlew compileTestJava
java -cp build/classes/java/test:build/classes/java/main:$(./gradlew -q printClasspath) com.kafka.monitor.TestDataGenerator
