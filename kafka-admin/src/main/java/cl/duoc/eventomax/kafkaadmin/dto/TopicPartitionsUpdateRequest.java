package cl.duoc.eventomax.kafkaadmin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record TopicPartitionsUpdateRequest(
        @Min(value = 1, message = "El número total de particiones debe ser al menos 1")
        @Max(value = 64, message = "El número total de particiones no debe exceder 64")
        int totalPartitions
) {}

