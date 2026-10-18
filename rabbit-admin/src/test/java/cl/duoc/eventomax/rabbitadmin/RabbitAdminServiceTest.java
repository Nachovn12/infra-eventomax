package cl.duoc.eventomax.rabbitadmin;
import cl.duoc.eventomax.rabbitadmin.dto.*;
import cl.duoc.eventomax.rabbitadmin.service.*;
import cl.duoc.eventomax.rabbitadmin.exception.*;
import com.rabbitmq.client.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.*;
import java.io.IOException;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class RabbitAdminServiceTest {
    AmqpAdmin admin;
    RabbitOperations operations;
    Channel channel;
    RabbitAdminService service;
    @BeforeEach void setup() {
        admin = mock(AmqpAdmin.class); operations = mock(RabbitOperations.class); channel = mock(Channel.class);
        service = new RabbitAdminService(admin, operations, new ResourcePolicy(), new BrokerFailureTranslator());
        when(operations.execute(any())).thenAnswer(i -> {
            try { return ((ChannelCallback<?>) i.getArgument(0)).doInRabbit(channel); }
            catch (IOException ex) { throw new AmqpIOException(ex); }
        });
    }
    static AmqpException brokerError(int code) {
        return new AmqpIOException(new IOException(new ShutdownSignalException(false, false,
                new AMQP.Channel.Close.Builder().replyCode(code).replyText("test failure").classId(50).methodId(10).build(), null)));
    }
    @Test void createsQueueAfterObservedAbsenceWithWhitelist() throws Exception {
        when(channel.queueDeclarePassive("demo.jobs")).thenThrow(brokerError(404));
        var result = service.createQueue(new QueueRequest("demo.jobs", true, 1000, 10, null, null));
        assertThat(result.outcome()).isEqualTo(DeclarationResponse.Outcome.DECLARED_AFTER_ABSENCE);
        verify(admin).declareQueue(argThat(q -> q.isDurable() && !q.isExclusive() && !q.isAutoDelete()
                && q.getArguments().equals(java.util.Map.of("x-queue-type","classic","x-message-ttl",1000,"x-max-length",10))));
    }
    @Test void existingQueueIsStillDeclaredToCheckCompatibility() throws Exception {
        when(channel.queueDeclarePassive("demo.jobs")).thenReturn(new AMQP.Queue.DeclareOk.Builder().queue("demo.jobs").messageCount(2).consumerCount(0).build());
        assertThat(service.createQueue(new QueueRequest("demo.jobs",true,null,null,null,null)).outcome())
                .isEqualTo(DeclarationResponse.Outcome.EXISTING_COMPATIBLE);
        verify(admin).declareQueue(any());
    }
    @Test void createsExchange() throws Exception {
        when(channel.exchangeDeclarePassive("demo.events")).thenThrow(brokerError(404));
        assertThat(service.createExchange(new ExchangeRequest("demo.events",ExchangeRequest.Type.topic,true)).outcome())
                .isEqualTo(DeclarationResponse.Outcome.DECLARED_AFTER_ABSENCE);
        verify(admin).declareExchange(argThat(e -> e.getName().equals("demo.events") && e.getType().equals("topic")));
    }
    @Test void createAndRemoveBinding() {
        var request = new BindingRequest("demo.events","demo.jobs","jobs.*");
        service.createBinding(request); service.deleteBinding(request);
        verify(admin).declareBinding(argThat(b -> b.getDestination().equals("demo.jobs") && b.getExchange().equals("demo.events")));
        verify(admin).removeBinding(any());
    }
    @Test void queueDeleteUsesAtomicBrokerGuards() {
        service.deleteQueue("demo.jobs"); verify(admin).deleteQueue("demo.jobs",true,true);
        verifyNoInteractions(operations);
        verify(admin, never()).deleteQueue(anyString());
    }
    @Test void exchangeDeleteIsConditional() throws Exception {
        service.deleteExchange("demo.events"); verify(channel).exchangeDelete("demo.events",true);
        verifyNoInteractions(admin);
    }
    @ParameterizedTest @ValueSource(strings={"q.cmd.email","q.cmd.email.dlq","q.cmd.crew","q.cmd.crew.dlq","q.cmd.quote","q.cmd.quote.dlq","cmd.direct","cmd.topic","cmd.dead.dlx","amq.direct"})
    void protectsOperationalNames(String name) {
        assertThatThrownBy(() -> service.deleteQueue(name)).isInstanceOfSatisfying(AdminException.class,e -> assertThat(e.status().value()).isEqualTo(403));
        assertThatThrownBy(() -> service.deleteExchange(name)).isInstanceOf(AdminException.class);
        verifyNoInteractions(admin, operations);
    }
    @Test void protectsDefaultExchangeAndBlankNames() {
        assertThatThrownBy(() -> service.deleteExchange("")).isInstanceOf(AdminException.class);
        verifyNoInteractions(admin,operations);
    }
    @Test void rejectsOperationalBindingAndDlx() {
        assertThatThrownBy(() -> service.createBinding(new BindingRequest("cmd.direct","demo.jobs","key"))).isInstanceOf(AdminException.class);
        assertThatThrownBy(() -> service.deleteBinding(new BindingRequest("demo.events","q.cmd.email","key"))).isInstanceOf(AdminException.class);
        assertThatThrownBy(() -> service.createQueue(new QueueRequest("demo.jobs",true,null,null,"cmd.dead.dlx",null))).isInstanceOf(AdminException.class);
        verifyNoInteractions(admin,operations);
    }
    @Test void rejectsRoutingKeyWithoutDlx() {
        assertThatThrownBy(() -> service.createQueue(new QueueRequest("demo.jobs",true,null,null,null,"key")))
                .isInstanceOfSatisfying(AdminException.class,e -> assertThat(e.status().value()).isEqualTo(400));
        verifyNoInteractions(admin,operations);
    }
    @Test void incompatibleDeclarationIsConflict() throws Exception {
        when(channel.exchangeDeclarePassive(anyString())).thenReturn(null);
        doThrow(brokerError(406)).when(admin).declareExchange(any());
        assertThatThrownBy(() -> service.createExchange(new ExchangeRequest("demo.events",ExchangeRequest.Type.direct,true)))
                .isInstanceOfSatisfying(AdminException.class,e -> assertThat(e.status().value()).isEqualTo(409));
    }
    @Test void nonemptyOrInUseQueueIsConflict() {
        doThrow(brokerError(406)).when(admin).deleteQueue("demo.jobs",true,true);
        assertThatThrownBy(() -> service.deleteQueue("demo.jobs"))
                .isInstanceOfSatisfying(AdminException.class,e -> assertThat(e.status().value()).isEqualTo(409));
    }
    @Test void absentDeleteIsIdempotent() {
        doThrow(brokerError(404)).when(admin).deleteQueue("demo.jobs",true,true);
        assertThatCode(() -> service.deleteQueue("demo.jobs")).doesNotThrowAnyException();
    }
    @Test void connectionFailureIsNotReportedAsAbsence() {
        doThrow(new AmqpConnectException(new java.net.ConnectException("private connection detail"))).when(operations).execute(any());
        assertThatThrownBy(() -> service.getQueue("demo.jobs"))
                .isInstanceOfSatisfying(AdminException.class,e -> {assertThat(e.status().value()).isEqualTo(503); assertThat(e.getMessage()).doesNotContain("private");});
        verifyNoInteractions(admin);
    }
    @Test void snapshotDoesNotInventProperties() throws Exception {
        when(channel.queueDeclarePassive("demo.jobs")).thenReturn(new AMQP.Queue.DeclareOk.Builder().queue("demo.jobs").messageCount(4).consumerCount(2).build());
        assertThat(service.getQueue("demo.jobs")).isEqualTo(new QueueResponse("demo.jobs",4,2));
    }
    @Test void brokerPermissionsAreForbidden() {
        doThrow(brokerError(403)).when(admin).declareBinding(any());
        assertThatThrownBy(() -> service.createBinding(new BindingRequest("demo.events","demo.jobs","key")))
                .isInstanceOfSatisfying(AdminException.class,e -> assertThat(e.status().value()).isEqualTo(403));
    }
    @Test void createsQueueWithExistingDeadLetterExchange() throws Exception {
        when(channel.exchangeDeclarePassive("demo.dlx")).thenReturn(null);
        when(channel.queueDeclarePassive("demo.jobs")).thenThrow(brokerError(404));
        var result = service.createQueue(new QueueRequest("demo.jobs", true, null, null, "demo.dlx", "demo.dead"));
        assertThat(result.outcome()).isEqualTo(DeclarationResponse.Outcome.DECLARED_AFTER_ABSENCE);
        verify(admin).declareQueue(argThat(q -> q.getArguments().equals(java.util.Map.of(
                "x-queue-type", "classic",
                "x-dead-letter-exchange", "demo.dlx",
                "x-dead-letter-routing-key", "demo.dead"))));
    }
    @Test void createQueueWithMissingDeadLetterExchangeIs404() throws Exception {
        when(channel.exchangeDeclarePassive("demo.missing-dlx")).thenThrow(brokerError(404));
        assertThatThrownBy(() -> service.createQueue(new QueueRequest("demo.jobs", true, null, null, "demo.missing-dlx", null)))
                .isInstanceOfSatisfying(AdminException.class, e -> assertThat(e.status().value()).isEqualTo(404));
        verifyNoInteractions(admin);
    }
    @Test void getMissingQueueIs404() throws Exception {
        when(channel.queueDeclarePassive("demo.missing")).thenThrow(brokerError(404));
        assertThatThrownBy(() -> service.getQueue("demo.missing"))
                .isInstanceOfSatisfying(AdminException.class, e -> assertThat(e.status().value()).isEqualTo(404));
    }
    @Test void inUseExchangeDeleteIsConflict409() throws Exception {
        when(channel.exchangeDelete("demo.events", true)).thenThrow(brokerError(406));
        assertThatThrownBy(() -> service.deleteExchange("demo.events"))
                .isInstanceOfSatisfying(AdminException.class, e -> assertThat(e.status().value()).isEqualTo(409));
    }
    @Test void resourcePolicyAllowsValidDemoName() {
        var policy = new ResourcePolicy();
        assertThatCode(() -> policy.requireDemo("demo.valid")).doesNotThrowAnyException();
    }
    @ParameterizedTest @ValueSource(strings={"demo.","demo.-invalid","demo._invalid","demo..invalid"})
    void resourcePolicyRejectsMalformedDemoSuffixWith400(String malformedDemoName) {
        var policy = new ResourcePolicy();
        assertThatThrownBy(() -> policy.requireDemo(malformedDemoName))
                .isInstanceOfSatisfying(AdminException.class, e -> assertThat(e.status().value()).isEqualTo(400));
    }
    @ParameterizedTest @ValueSource(strings={"q.cmd.crew.dlq","cmd.direct","q.cmd.email","other.namespace","demo"})
    void resourcePolicyRejectsOutsideNamespaceWith403(String nonDemoName) {
        var policy = new ResourcePolicy();
        assertThatThrownBy(() -> policy.requireDemo(nonDemoName))
                .isInstanceOfSatisfying(AdminException.class, e -> assertThat(e.status().value()).isEqualTo(403));
    }
}

