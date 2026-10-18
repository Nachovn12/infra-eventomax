package cl.duoc.eventomax.rabbitadmin.service;
import cl.duoc.eventomax.rabbitadmin.dto.*;
import cl.duoc.eventomax.rabbitadmin.exception.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@Service
@Validated
public class RabbitAdminService {
    private final AmqpAdmin admin;
    private final RabbitOperations operations;
    private final ResourcePolicy policy;
    private final BrokerFailureTranslator failures;
    public RabbitAdminService(AmqpAdmin admin, RabbitOperations operations, ResourcePolicy policy, BrokerFailureTranslator failures) {
        this.admin = admin; this.operations = operations; this.policy = policy; this.failures = failures;
    }
    public DeclarationResponse createQueue(@Valid @NotNull QueueRequest request) {
        policy.requireDemo(request.name());
        Map<String,Object> arguments = new HashMap<>();
        arguments.put("x-queue-type", "classic");
        if (request.messageTtlMs() != null) arguments.put("x-message-ttl", request.messageTtlMs());
        if (request.maxLength() != null) arguments.put("x-max-length", request.maxLength());
        if (request.deadLetterRoutingKey() != null && request.deadLetterExchange() == null)
            throw new AdminException(HttpStatus.BAD_REQUEST, "deadLetterRoutingKey requires deadLetterExchange");
        if (request.deadLetterExchange() != null) {
            policy.requireDemo(request.deadLetterExchange());
            if (!exchangeExists(request.deadLetterExchange()))
                throw new AdminException(HttpStatus.NOT_FOUND, "Dead letter exchange does not exist");
            arguments.put("x-dead-letter-exchange", request.deadLetterExchange());
            if (request.deadLetterRoutingKey() != null) arguments.put("x-dead-letter-routing-key", request.deadLetterRoutingKey());
        }
        boolean existed = queueSnapshot(request.name()) != null;
        return invoke(() -> {
            admin.declareQueue(new Queue(request.name(), request.durable(), false, false, arguments));
            return declaration(request.name(), existed);
        });
    }
    public QueueResponse getQueue(String name) {
        policy.requireDemo(name);
        QueueResponse result = queueSnapshot(name);
        if (result == null) throw new AdminException(HttpStatus.NOT_FOUND, "Queue does not exist");
        return result;
    }
    public void deleteQueue(String name) {
        policy.requireDemo(name);
        // Broker checks both conditions atomically. A prior GET would not protect against races.
        deleteIdempotently(() -> admin.deleteQueue(name, true, true));
    }
    public DeclarationResponse createExchange(@Valid @NotNull ExchangeRequest request) {
        policy.requireDemo(request.name());
        boolean existed = exchangeExists(request.name());
        return invoke(() -> {
            AbstractExchange exchange = switch (request.type()) {
                case direct -> new DirectExchange(request.name(), request.durable(), false);
                case topic -> new TopicExchange(request.name(), request.durable(), false);
                case fanout -> new FanoutExchange(request.name(), request.durable(), false);
            };
            admin.declareExchange(exchange);
            return declaration(request.name(), existed);
        });
    }
    public void deleteExchange(String name) {
        policy.requireDemo(name);
        // AmqpAdmin.deleteExchange has no ifUnused overload; use Spring's channel lifecycle abstraction.
        deleteIdempotently(() -> operations.execute(channel -> channel.exchangeDelete(name, true)));
    }
    public void createBinding(@Valid @NotNull BindingRequest request) {
        validateBinding(request);
        invoke(() -> { admin.declareBinding(binding(request)); return null; });
    }
    public void deleteBinding(@Valid @NotNull BindingRequest request) {
        validateBinding(request);
        deleteIdempotently(() -> admin.removeBinding(binding(request)));
    }
    private void validateBinding(BindingRequest request) {
        policy.requireDemo(request.exchange()); policy.requireDemo(request.queue());
    }
    private Binding binding(BindingRequest request) {
        return new Binding(request.queue(), Binding.DestinationType.QUEUE, request.exchange(), request.routingKey(), Map.of());
    }
    private DeclarationResponse declaration(String name, boolean existed) {
        return new DeclarationResponse(name, existed ? DeclarationResponse.Outcome.EXISTING_COMPATIBLE : DeclarationResponse.Outcome.DECLARED_AFTER_ABSENCE);
    }
    private QueueResponse queueSnapshot(String name) {
        try {
            // Unlike getQueueInfo, passive declaration here preserves non-404 broker failures.
            return operations.execute(channel -> {
                var result = channel.queueDeclarePassive(name);
                return new QueueResponse(result.getQueue(), result.getMessageCount(), result.getConsumerCount());
            });
        } catch (AmqpException ex) {
            if (failures.replyCode(ex) == 404) return null;
            throw failures.translate(ex);
        }
    }
    private boolean exchangeExists(String name) {
        try {
            operations.execute(channel -> channel.exchangeDeclarePassive(name));
            return true;
        } catch (AmqpException ex) {
            if (failures.replyCode(ex) == 404) return false;
            throw failures.translate(ex);
        }
    }
    private <T> T invoke(Supplier<T> action) {
        try { return action.get(); } catch (AmqpException ex) { throw failures.translate(ex); }
    }
    private void deleteIdempotently(Runnable action) {
        try { action.run(); } catch (AmqpException ex) {
            if (failures.replyCode(ex) != 404) throw failures.translate(ex);
        }
    }
}
