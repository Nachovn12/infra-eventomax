package cl.duoc.eventomax.kafkaadmin.dto;

import java.util.List;
import java.util.Map;

public record TopicResponse(
        String name,
        int partitionsCount,
        int replicationFactor,
        List<PartitionInfoResponse> partitions,
        Map<String, String> configs
) {
    public record PartitionInfoResponse(
            int partition,
            int leaderId,
            List<Integer> replicas,
            List<Integer> isr
    ) {}
}

