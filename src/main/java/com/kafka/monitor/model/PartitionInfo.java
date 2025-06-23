package com.kafka.monitor.model;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class PartitionInfo {
    private int partition;
    private long beginningOffset;
    private long endOffset;
    private int leader;
    private int[] replicas;
    private int[] inSyncReplicas;
    private long messageCount;

    public long getMessageCount() {
        return endOffset - beginningOffset;
    }
}
