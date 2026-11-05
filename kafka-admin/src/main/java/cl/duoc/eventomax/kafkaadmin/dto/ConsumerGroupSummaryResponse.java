package cl.duoc.eventomax.kafkaadmin.dto;

public record ConsumerGroupSummaryResponse(
        String groupId,
        String state,
        boolean simpleConsumerGroup
) {}

