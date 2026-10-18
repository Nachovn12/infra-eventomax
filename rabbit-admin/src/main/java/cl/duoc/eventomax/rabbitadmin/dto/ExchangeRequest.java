package cl.duoc.eventomax.rabbitadmin.dto;
import jakarta.validation.constraints.NotNull;
public record ExchangeRequest(@ResourceName String name, @NotNull Type type, @NotNull Boolean durable) {
    public enum Type { direct, topic, fanout }
}
