package com.kafka.monitor.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.EnableWebFlux;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebFlux
@RequiredArgsConstructor
public class KafkaConfig {

    @Bean
    @ConfigurationProperties(prefix = "kafka")
    public KafkaProperties kafkaProperties() {
        return new KafkaProperties();
    }

    @Bean
    public KafkaClusterManager kafkaClusterManager(KafkaProperties kafkaProperties) {
        return new KafkaClusterManager(kafkaProperties);
    }
}
