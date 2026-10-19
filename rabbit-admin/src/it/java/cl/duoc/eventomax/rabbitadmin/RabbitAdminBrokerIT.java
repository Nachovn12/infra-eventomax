package cl.duoc.eventomax.rabbitadmin;
import cl.duoc.eventomax.rabbitadmin.dto.*;
import cl.duoc.eventomax.rabbitadmin.service.*;
import cl.duoc.eventomax.rabbitadmin.exception.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Explicit opt-in, fresh container, random mapped port; never uses application environment/configuration. */
@Testcontainers
@EnabledIfEnvironmentVariable(named="EVENTOMAX_RUN_ISOLATED_RABBIT_IT", matches="true")
class RabbitAdminBrokerIT {
    private static final String PASSWORD = UUID.randomUUID().toString();
    @Container
    static final GenericContainer<?> BROKER = new GenericContainer<>(DockerImageName.parse("rabbitmq:4.3.6-management"))
            .withEnv("RABBITMQ_DEFAULT_USER","isolated-test-user").withEnv("RABBITMQ_DEFAULT_PASS",PASSWORD)
            .withExposedPorts(5672).waitingFor(Wait.forListeningPort());
    CachingConnectionFactory connection;
    RabbitTemplate template;
    RabbitAdminService service;
    String name;
    @BeforeEach void isolatedFactory() {
        assertThat(BROKER.isRunning()).isTrue();
        connection = new CachingConnectionFactory(BROKER.getHost(), BROKER.getMappedPort(5672));
        connection.setUsername("isolated-test-user"); connection.setPassword(PASSWORD); connection.setVirtualHost("/");
        var admin = new RabbitAdmin(connection); admin.setAutoStartup(false); admin.setIgnoreDeclarationExceptions(false);
        template = new RabbitTemplate(connection);
        service = new RabbitAdminService(admin,template,new ResourcePolicy(),new BrokerFailureTranslator());
        name="demo.it-"+UUID.randomUUID();
    }
    @AfterEach void closeConnection() { if(connection!=null) connection.destroy(); }
    QueueRequest queue() { return new QueueRequest(name,true,null,null,null,null); }
    @Test void declarationReplayAndIncompatibility() {
        assertThat(service.createQueue(queue()).outcome()).isEqualTo(DeclarationResponse.Outcome.DECLARED_AFTER_ABSENCE);
        assertThat(service.createQueue(queue()).outcome()).isEqualTo(DeclarationResponse.Outcome.EXISTING_COMPATIBLE);
        assertThatThrownBy(() -> service.createQueue(new QueueRequest(name,false,null,null,null,null)))
                .isInstanceOfSatisfying(AdminException.class,e -> assertThat(e.status().value()).isEqualTo(409));
    }
    @Test void nonemptyQueueCannotBeDeleted() {
        service.createQueue(queue());
        template.convertAndSend("",name,"isolated-test-message");
        assertThatThrownBy(() -> service.deleteQueue(name)).isInstanceOfSatisfying(AdminException.class,e -> assertThat(e.status().value()).isEqualTo(409));
        assertThat(service.getQueue(name).readyMessages()).isEqualTo(1);
    }
    @Test void activeConsumerPreventsDeletion() {
        service.createQueue(queue());
        template.execute(channel -> {
            String tag=channel.basicConsume(name,true,(consumerTag,message)->{},consumerTag->{});
            try {
                assertThatThrownBy(() -> service.deleteQueue(name)).isInstanceOfSatisfying(AdminException.class,e -> assertThat(e.status().value()).isEqualTo(409));
            } finally { channel.basicCancel(tag); }
            return null;
        });
    }
    @Test void exchangeBindingsAndConditionalDeletion() {
        String exchange=name+".events";
        service.createQueue(queue());
        assertThat(service.createExchange(new ExchangeRequest(exchange,ExchangeRequest.Type.topic,true)).outcome()).isEqualTo(DeclarationResponse.Outcome.DECLARED_AFTER_ABSENCE);
        assertThat(service.createExchange(new ExchangeRequest(exchange,ExchangeRequest.Type.topic,true)).outcome()).isEqualTo(DeclarationResponse.Outcome.EXISTING_COMPATIBLE);
        var binding=new BindingRequest(exchange,name,"jobs.*");
        service.createBinding(binding); service.createBinding(binding);
        assertThatThrownBy(() -> service.deleteExchange(exchange)).isInstanceOfSatisfying(AdminException.class,e -> assertThat(e.status().value()).isEqualTo(409));
        service.deleteBinding(binding); service.deleteBinding(binding);
        service.deleteExchange(exchange); service.deleteQueue(name); service.deleteQueue(name);
    }
    @Test void operationalResourcesAreRejectedEvenOnIsolatedBroker() {
        assertThatThrownBy(() -> service.deleteQueue("q.cmd.crew.dlq"))
                .isInstanceOfSatisfying(AdminException.class,e -> assertThat(e.status().value()).isEqualTo(403));
    }
}
