package cl.duoc.eventomax.kafkaadmin.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

public record TopicConfigUpdateRequest(
        @NotEmpty(message = "El mapa de configuraciones no puede estar vacío")
        Map<String, String> configs
) {}

