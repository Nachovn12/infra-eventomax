package cl.duoc.eventomax.rabbitadmin;
import cl.duoc.eventomax.rabbitadmin.dto.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.oauth2.jwt.*;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"debug=false", "logging.level.root=WARN", "logging.level.org.springframework=WARN", "RABBITMQ_HOST=127.0.0.1", "RABBITMQ_PORT=1", "RABBITMQ_VHOST=/isolated-test",
        "RABBITMQ_USERNAME=unused", "RABBITMQ_PASSWORD=unused", "ENTRA_ISSUER_URI=https://issuer.invalid",
        "ENTRA_AUDIENCE=api", "management.health.rabbit.enabled=false",
        "management.endpoint.health.group.readiness.include=readinessState"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class RabbitAdminHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean AmqpAdmin admin;
    @MockitoBean RabbitTemplate template;
    @MockitoBean JwtDecoder decoder;
    static final String BASE="/api/rabbit-admin";
    static final String VALID="{\"name\":\"demo.jobs\",\"durable\":true}";
    @BeforeEach void authenticatedAdmin() { clearInvocations(admin, template); identity(List.of("Admin"), "access_as_user"); }
    void identity(List<String> roles, String scope) {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token").header("alg","RS256")
                .issuer("https://issuer.invalid").audience(List.of("api")).subject("test-admin")
                .expiresAt(Instant.now().plusSeconds(120)).claim("roles",roles).claim("scp",scope).build());
    }
    @Test void withoutJwtIs401() throws Exception {
        mvc.perform(post(BASE+"/queues").contentType("application/json").content(VALID)).andExpect(status().isUnauthorized());
        verifyNoInteractions(admin,template);
    }
    @Test void invalidJwtIs401() throws Exception {
        when(decoder.decode("test-token")).thenThrow(new BadJwtException("invalid test token"));
        mvc.perform(get(BASE+"/queues/demo.jobs").header("Authorization","Bearer test-token")).andExpect(status().isUnauthorized());
        verifyNoInteractions(admin,template);
    }
    @ParameterizedTest @ValueSource(strings={"Productor","Organizador","Auditor","Producer","admin"})
    void wrongRoleIs403(String role) throws Exception {
        identity(List.of(role),"access_as_user");
        mvc.perform(post(BASE+"/queues").header("Authorization","Bearer test-token").contentType("application/json").content(VALID)).andExpect(status().isForbidden());
        verifyNoInteractions(admin,template);
    }
    @Test void adminWithoutScopeIs403() throws Exception {
        identity(List.of("Admin"),"other_scope");
        mvc.perform(delete(BASE+"/queues/demo.jobs").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
        verifyNoInteractions(admin,template);
    }
    @Test void validAdminCanCreateQueue() throws Exception {
        mvc.perform(post(BASE+"/queues").header("Authorization","Bearer test-token").contentType("application/json").content(VALID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("DECLARED_AFTER_ABSENCE"));
        verify(admin).declareQueue(any());
    }
    @ParameterizedTest @ValueSource(strings={
        "{\"name\":\"\",\"durable\":true}",
        "{\"name\":\"demo.bad/name\",\"durable\":true}",
        "{\"name\":\"demo.jobs\"}",
        "{\"name\":\"demo.jobs\",\"durable\":null}",
        "{\"name\":\"demo.jobs\",\"durable\":true,\"arguments\":{}}",
        "{\"name\":\"demo.jobs\",\"durable\":true,\"exclusive\":true}",
        "{\"name\":\"demo.jobs\",\"durable\":true,\"messageTtlMs\":0}",
        "{\"name\":\"demo.jobs\",\"durable\":true,\"deadLetterRoutingKey\":\"key\"}"
    })
    void invalidRequestIs400BeforeBrokerAccess(String body) throws Exception {
        mvc.perform(post(BASE+"/queues").header("Authorization","Bearer test-token").contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        verifyNoInteractions(admin,template);
    }
    @Test void outsideNamespaceIs403() throws Exception {
        mvc.perform(post(BASE+"/queues").header("Authorization","Bearer test-token").contentType("application/json")
                .content("{\"name\":\"q.cmd.email\",\"durable\":true}")).andExpect(status().isForbidden());
        verifyNoInteractions(admin,template);
    }
    @ParameterizedTest @ValueSource(strings={"q.cmd.email","q.cmd.crew.dlq","q.cmd.quote","amq.direct"})
    void protectedDeletionIs403(String name) throws Exception {
        mvc.perform(delete(BASE+"/queues/"+name).header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
        verifyNoInteractions(admin,template);
    }
    @Test void brokerUnavailableIs503() throws Exception {
        when(template.execute(any())).thenThrow(new org.springframework.amqp.AmqpConnectException(new java.net.ConnectException("sensitive-host")));
        mvc.perform(get(BASE+"/queues/demo.jobs").header("Authorization","Bearer test-token"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sensitive-host"))));
    }
    @Test void incompatibleQueueIs409() throws Exception {
        doThrow(RabbitAdminServiceTest.brokerError(406)).when(admin).declareQueue(any());
        mvc.perform(post(BASE+"/queues").header("Authorization","Bearer test-token").contentType("application/json").content(VALID)).andExpect(status().isConflict());
    }
    @Test void occupiedQueueIs409() throws Exception {
        doThrow(RabbitAdminServiceTest.brokerError(406)).when(admin).deleteQueue("demo.jobs",true,true);
        mvc.perform(delete(BASE+"/queues/demo.jobs").header("Authorization","Bearer test-token")).andExpect(status().isConflict());
    }
    @Test void deleteQueueReturns204() throws Exception {
        mvc.perform(delete(BASE+"/queues/demo.jobs").header("Authorization","Bearer test-token")).andExpect(status().isNoContent());
        verify(admin).deleteQueue("demo.jobs",true,true);
    }
    @Test void healthIsPublicButInfoIsNot() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
        verifyNoInteractions(admin,template);
    }
    @Test void invalidExchangeTypeIs400() throws Exception {
        mvc.perform(post(BASE+"/exchanges").header("Authorization","Bearer test-token").contentType("application/json")
                .content("{\"name\":\"demo.events\",\"durable\":true,\"type\":\"headers\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(admin,template);
    }
    @Test void bindingEndpointsAndValidation() throws Exception {
        String body="{\"exchange\":\"demo.events\",\"queue\":\"demo.jobs\",\"routingKey\":\"jobs.*\"}";
        mvc.perform(post(BASE+"/bindings").header("Authorization","Bearer test-token").contentType("application/json").content(body)).andExpect(status().isNoContent());
        mvc.perform(delete(BASE+"/bindings").header("Authorization","Bearer test-token").contentType("application/json").content(body)).andExpect(status().isNoContent());
        verify(admin).declareBinding(any()); verify(admin).removeBinding(any());
    }
    @Test void createExchangeReturns200() throws Exception {
        when(template.execute(any())).thenThrow(RabbitAdminServiceTest.brokerError(404));
        mvc.perform(post(BASE+"/exchanges").header("Authorization","Bearer test-token").contentType("application/json")
                .content("{\"name\":\"demo.events\",\"type\":\"topic\",\"durable\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("demo.events"))
                .andExpect(jsonPath("$.outcome").value("DECLARED_AFTER_ABSENCE"));
        verify(admin).declareExchange(any());
    }
    @Test void deleteExchangeReturns204() throws Exception {
        mvc.perform(delete(BASE+"/exchanges/demo.events").header("Authorization","Bearer test-token"))
                .andExpect(status().isNoContent());
        verify(template).execute(any());
    }
    @Test void deleteExchangeInUseReturns409() throws Exception {
        when(template.execute(any())).thenThrow(RabbitAdminServiceTest.brokerError(406));
        mvc.perform(delete(BASE+"/exchanges/demo.events").header("Authorization","Bearer test-token"))
                .andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
    @Test void createQueueWithExistingDlxReturns200() throws Exception {
        when(template.execute(any())).thenReturn(null).thenThrow(RabbitAdminServiceTest.brokerError(404));
        mvc.perform(post(BASE+"/queues").header("Authorization","Bearer test-token").contentType("application/json")
                .content("{\"name\":\"demo.jobs\",\"durable\":true,\"deadLetterExchange\":\"demo.dlx\",\"deadLetterRoutingKey\":\"demo.dead\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("DECLARED_AFTER_ABSENCE"));
        verify(admin).declareQueue(any());
    }
    @Test void createQueueWithMissingDlxReturns404() throws Exception {
        when(template.execute(any())).thenThrow(RabbitAdminServiceTest.brokerError(404));
        mvc.perform(post(BASE+"/queues").header("Authorization","Bearer test-token").contentType("application/json")
                .content("{\"name\":\"demo.jobs\",\"durable\":true,\"deadLetterExchange\":\"demo.missing-dlx\"}"))
                .andExpect(status().isNotFound()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        verifyNoInteractions(admin);
    }
    @Test void getMissingQueueReturns404() throws Exception {
        when(template.execute(any())).thenThrow(RabbitAdminServiceTest.brokerError(404));
        mvc.perform(get(BASE+"/queues/demo.missing").header("Authorization","Bearer test-token"))
                .andExpect(status().isNotFound()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
    @ParameterizedTest @ValueSource(strings={"demo.","demo.-invalid","demo._invalid"})
    void malformedDemoNameReturns400(String malformedDemoName) throws Exception {
        mvc.perform(delete(BASE+"/queues/"+malformedDemoName).header("Authorization","Bearer test-token"))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        verifyNoInteractions(admin,template);
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
            "debug=false", "logging.level.root=WARN", "logging.level.org.springframework=WARN",
            "RABBITMQ_HOST=127.0.0.1", "RABBITMQ_PORT=1", "RABBITMQ_VHOST=/isolated-test",
            "RABBITMQ_USERNAME=unused", "RABBITMQ_PASSWORD=unused", "ENTRA_ISSUER_URI=https://issuer.invalid",
            "ENTRA_AUDIENCE=api", "management.health.rabbit.enabled=false",
            "management.endpoint.health.group.readiness.include=readinessState"
    })
    class EmbeddedServletContainerErrorHandlingTest {
        @org.springframework.boot.test.web.server.LocalServerPort int port;

        private java.net.http.HttpResponse<String> send(String method, String path, String contentType, String body, String token) throws Exception {
            var builder = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + path));
            if (token != null) builder.header("Authorization", "Bearer " + token);
            if (contentType != null) builder.header("Content-Type", contentType);
            var publisher = body == null ? java.net.http.HttpRequest.BodyPublishers.noBody() : java.net.http.HttpRequest.BodyPublishers.ofString(body);
            return java.net.http.HttpClient.newHttpClient().send(builder.method(method, publisher).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        }

        @Test void unmappedAdminRouteReturnsAuthentic404InEmbeddedContainer() throws Exception {
            var res = send("GET", BASE + "/unknown-endpoint", null, null, "test-token");
            org.assertj.core.api.Assertions.assertThat(res.statusCode()).isEqualTo(404);
            org.assertj.core.api.Assertions.assertThat(res.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        }

        @Test void unsupportedMethodReturnsAuthentic405InEmbeddedContainer() throws Exception {
            var res = send("PUT", BASE + "/queues", "application/json", VALID, "test-token");
            org.assertj.core.api.Assertions.assertThat(res.statusCode()).isEqualTo(405);
            org.assertj.core.api.Assertions.assertThat(res.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        }

        @Test void unsupportedMediaTypeReturnsAuthentic415InEmbeddedContainer() throws Exception {
            var res = send("POST", BASE + "/queues", "text/plain", VALID, "test-token");
            org.assertj.core.api.Assertions.assertThat(res.statusCode()).isEqualTo(415);
            org.assertj.core.api.Assertions.assertThat(res.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        }

        @Test void unauthenticatedAndUnauthorizedRemainFailClosedInEmbeddedContainer() throws Exception {
            org.assertj.core.api.Assertions.assertThat(send("GET", BASE + "/unknown-endpoint", null, null, null).statusCode()).isEqualTo(401);
            when(decoder.decode("viewer-token")).thenReturn(Jwt.withTokenValue("viewer-token").header("alg", "RS256")
                    .issuer("https://issuer.invalid").audience(List.of("api")).subject("test-auditor")
                    .expiresAt(Instant.now().plusSeconds(120)).claim("roles", List.of("Auditor")).claim("scp", "access_as_user").build());
            org.assertj.core.api.Assertions.assertThat(send("GET", BASE + "/unknown-endpoint", null, null, "viewer-token").statusCode()).isEqualTo(403);
        }
    }
}


