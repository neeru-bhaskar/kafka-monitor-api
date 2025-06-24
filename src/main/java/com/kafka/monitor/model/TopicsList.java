package com.kafka.monitor.model;

import java.util.List;

public class TopicsList {

    private List<String> topics;

    public TopicsList(List<String> topics) {
        this.topics = topics;
    }

    public List<String> getTopics() {
        return topics;
    }

    public void setTopics(List<String> topics) {
        this.topics = topics;
    }
}
