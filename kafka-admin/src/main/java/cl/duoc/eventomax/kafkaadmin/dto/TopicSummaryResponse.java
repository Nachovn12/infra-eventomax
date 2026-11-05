package cl.duoc.eventomax.kafkaadmin.dto;

public record TopicSummaryResponse(
        String name,
        boolean internal,
        int partitionsCount
) {}

