package cl.duoc.eventomax.rabbitadmin.dto;
import jakarta.validation.constraints.*;
public record QueueRequest(@ResourceName String name, @NotNull Boolean durable,
        @Min(1) @Max(86400000) Integer messageTtlMs,
        @Min(1) @Max(100000) Integer maxLength,
        @Size(max=128) @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._-]*") String deadLetterExchange,
        @Size(max=128) @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._-]*") String deadLetterRoutingKey) {}
