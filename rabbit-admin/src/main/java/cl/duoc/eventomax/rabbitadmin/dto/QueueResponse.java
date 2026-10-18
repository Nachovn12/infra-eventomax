package cl.duoc.eventomax.rabbitadmin.dto;
/** Passive AMQP declaration exposes counts, not durability or arguments. Counts are instantaneous. */
public record QueueResponse(String name, int readyMessages, int consumers) {}
