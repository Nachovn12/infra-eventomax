package cl.duoc.eventomax.kafkaadmin.dto;

public record TopicOperationResponse(
        String topic,
        String status,
        String message
) {}

