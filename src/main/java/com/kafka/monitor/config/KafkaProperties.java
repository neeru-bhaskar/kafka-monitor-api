package com.kafka.monitor.config;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class KafkaProperties {
    private List<KafkaClusterProperties> clusters = new ArrayList<>();
}
