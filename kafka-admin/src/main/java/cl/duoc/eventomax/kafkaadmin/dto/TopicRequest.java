package cl.duoc.eventomax.kafkaadmin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

public record TopicRequest(
        @NotBlank(message = "El nombre del tópico es obligatorio")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "El nombre del tópico solo puede contener letras, números, puntos, guiones y guiones bajos")
        String name,

        @Min(value = 1, message = "El número de particiones debe ser al menos 1")
        @Max(value = 100, message = "El número de particiones no puede exceder 100")
        int partitions,

        @Min(value = 1, message = "El factor de replicación debe ser al menos 1")
        @Max(value = 10, message = "El factor de replicación no puede exceder 10")
        short replicationFactor,

        Map<String, String> configs
) {
    public TopicRequest {
        if (partitions <= 0) partitions = 3;
        if (replicationFactor <= 0) replicationFactor = 3;
    }
}

