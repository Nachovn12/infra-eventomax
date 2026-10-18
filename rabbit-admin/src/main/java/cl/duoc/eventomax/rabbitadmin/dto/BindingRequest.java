package cl.duoc.eventomax.rabbitadmin.dto;
import jakarta.validation.constraints.*;
public record BindingRequest(@ResourceName String exchange, @ResourceName String queue,
        @NotNull @Size(max=128) @Pattern(regexp="[A-Za-z0-9.*#_-]*") String routingKey) {}
