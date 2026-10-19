package cl.duoc.eventomax.rabbitadmin;

import com.rabbitmq.client.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Opt-in contract test for the NEW disposable bootstrap project, never the existing broker. */
@EnabledIfEnvironmentVariable(named="EVENTOMAX_TEST_LOCAL_IDENTITIES", matches="new-isolated-broker")
class LocalIdentitiesTest {
    @BeforeAll
    static void verifyIsolationSafety() {
        String brokerId = System.getenv("EVENTOMAX_TEST_BROKER_ID");
        if (brokerId == null || brokerId.isBlank()) {
            throw new IllegalStateException("EVENTOMAX_TEST_BROKER_ID is required to execute isolated contract tests.");
        }
        if ("eventomax-ep2-demo-20261019".equalsIgnoreCase(brokerId.trim())) {
            throw new IllegalStateException("Safety violation: EVENTOMAX_TEST_BROKER_ID must NOT target the academic demo broker (eventomax-ep2-demo-20261019)!");
        }
    }

    ConnectionFactory factory(String role) throws Exception {
        String credsPath = System.getenv().getOrDefault("EVENTOMAX_TEST_CREDENTIALS_PATH", "../mq/.local-secrets/credentials.json");
        var root = JsonMapper.builder().build().readTree(Files.readString(Path.of(credsPath)));
        var factory = new ConnectionFactory();
        String host = System.getenv().getOrDefault("EVENTOMAX_TEST_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("EVENTOMAX_TEST_PORT", "25692"));
        factory.setHost(host); factory.setPort(port); factory.setVirtualHost("/");
        factory.setUsername(root.get(role).get("username").asText());
        factory.setPassword(root.get(role).get("password").asText());
        factory.setAutomaticRecoveryEnabled(false);
        return factory;
    }
    Connection connect(String role) throws Exception { return factory(role).newConnection(); }
    void forbidden(String role, ChannelAction action) throws Exception {
        try (var connection = connect(role)) {
            var channel = connection.createChannel();
            assertThatThrownBy(() -> action.run(channel))
                .satisfies(error -> {
                    Throwable cause = error;
                    while (!(cause instanceof ShutdownSignalException) && cause.getCause() != null)
                        cause = cause.getCause();
                    assertThat(cause).isInstanceOf(ShutdownSignalException.class);
                    var reason = (AMQP.Channel.Close)((ShutdownSignalException)cause).getReason();
                    assertThat(reason.getReplyCode()).isEqualTo(403);
                });
        }
    }
    @FunctionalInterface interface ChannelAction { void run(Channel channel) throws IOException; }

    @Test void demoIdentityCanDeclareBindAndDelete() throws Exception {
        try (var c = connect("rabbit_admin"); var ch = c.createChannel()) {
            String name = "demo.test-" + UUID.randomUUID();
            ch.exchangeDeclare(name, "direct", true);
            ch.queueDeclare(name, true, false, false, Map.of());
            ch.queueBind(name, name, "test");
            ch.queueUnbind(name, name, "test");
            ch.queueDelete(name, true, true); ch.exchangeDelete(name, true);
        }
    }
    @Test void actualAdminServiceWorksWithRestrictedIdentity() throws Exception {
        var connection = new org.springframework.amqp.rabbit.connection.CachingConnectionFactory(factory("rabbit_admin"));
        try {
            var admin = new org.springframework.amqp.rabbit.core.RabbitAdmin(connection);
            admin.setAutoStartup(false);
            var service = new cl.duoc.eventomax.rabbitadmin.service.RabbitAdminService(admin,
                new org.springframework.amqp.rabbit.core.RabbitTemplate(connection),
                new cl.duoc.eventomax.rabbitadmin.service.ResourcePolicy(),
                new cl.duoc.eventomax.rabbitadmin.exception.BrokerFailureTranslator());
            String name = "demo.service-" + UUID.randomUUID();
            var queue = new cl.duoc.eventomax.rabbitadmin.dto.QueueRequest(name, true, null, null, null, null);
            service.createExchange(new cl.duoc.eventomax.rabbitadmin.dto.ExchangeRequest(name,
                cl.duoc.eventomax.rabbitadmin.dto.ExchangeRequest.Type.direct, true));
            service.createQueue(queue);
            assertThat(service.createQueue(queue).outcome().name()).isEqualTo("EXISTING_COMPATIBLE");
            assertThat(service.getQueue(name).readyMessages()).isZero();
            var binding = new cl.duoc.eventomax.rabbitadmin.dto.BindingRequest(name, name, "demo.route");
            service.createBinding(binding); service.deleteBinding(binding);
            service.deleteQueue(name); service.deleteExchange(name);
        } finally { connection.destroy(); }
    }
    @Test void demoIdentityCannotModifyOperationalResources() throws Exception {
        forbidden("rabbit_admin", ch -> ch.queueDelete("q.cmd.email", true, true));
        forbidden("rabbit_admin", ch -> ch.exchangeDelete("cmd.direct", true));
    }
    @Test void operationalIdentityCannotCreateDemoResources() throws Exception {
        forbidden("application", ch -> ch.queueDeclare("demo.denied", true, false, false, Map.of()));
    }
    @Test void observerCannotDeclarePublishOrConsume() throws Exception {
        forbidden("management", ch -> ch.queueDeclare("demo.denied", true, false, false, Map.of()));
        forbidden("management", ch -> ch.basicGet("q.cmd.email", false));
        forbidden("management", ch -> {
            ch.confirmSelect();
            ch.basicPublish("cmd.direct", "email.send", null, new byte[0]);
            // Synchronous operation observes the preceding asynchronous publish rejection.
            ch.queueDeclarePassive("q.cmd.email");
        });
    }
    @Test void allThreeOperationalFlowsAckAndDeadLetter() throws Exception {
        try (var c = connect("application"); var ch = c.createChannel()) {
            String[][] routes = {{"email", "email.send"}, {"crew", "crew.ticket"}, {"quote", "quote.gen"}};
            for (var route : routes) {
                String queue = "q.cmd." + route[0];
                byte[] body = ("isolated-" + UUID.randomUUID()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                ch.confirmSelect();
                ch.basicPublish("cmd.direct", route[1], new AMQP.BasicProperties.Builder().deliveryMode(2).build(), body);
                ch.waitForConfirmsOrDie(5000);
                var message = receive(ch, queue);
                assertThat(message.getBody()).isEqualTo(body);
                ch.basicNack(message.getEnvelope().getDeliveryTag(), false, false);
                var dead = receive(ch, queue + ".dlq");
                assertThat(dead.getBody()).isEqualTo(body);
                assertThat(dead.getProps().getHeaders()).containsKey("x-death");
                ch.basicAck(dead.getEnvelope().getDeliveryTag(), false);
                ch.basicPublish("cmd.direct", route[1], null, body);
                ch.waitForConfirmsOrDie(5000);
                var success = receive(ch, queue);
                ch.basicAck(success.getEnvelope().getDeliveryTag(), false);
            }
        }
    }
    @Test void restartPersistence() throws Exception {
        String phase = System.getenv("EVENTOMAX_RESTART_PHASE");
        Assumptions.assumeTrue("seed".equals(phase) || "verify".equals(phase));
        try (var c = connect("rabbit_admin"); var ch = c.createChannel()) {
            String name = "demo.restart-proof";
            if ("seed".equals(phase)) {
                ch.exchangeDeclare(name, "direct", true);
                ch.queueDeclare(name, true, false, false, Map.of());
                ch.queueBind(name, name, "proof");
                ch.confirmSelect();
                ch.basicPublish(name, "proof", new AMQP.BasicProperties.Builder().deliveryMode(2).build(),
                                "persistent-proof".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                ch.waitForConfirmsOrDie(5000);
            } else {
                assertThat(ch.queueDeclarePassive(name).getMessageCount()).isEqualTo(1);
                var message = receive(ch, name);
                assertThat(new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("persistent-proof");
                ch.basicAck(message.getEnvelope().getDeliveryTag(), false);
                ch.queueUnbind(name, name, "proof");
                ch.queueDelete(name, true, true); ch.exchangeDelete(name, true);
            }
        }
    }
    GetResponse receive(Channel channel, String queue) throws Exception {
        for (int i=0; i<50; i++) {
            var result = channel.basicGet(queue, false);
            if (result != null) return result;
            Thread.sleep(100);
        }
        throw new AssertionError("Message did not arrive in isolated test queue");
    }
}
