package cl.duoc.eventomax.kafkaadmin;

import cl.duoc.eventomax.kafkaadmin.dto.ConsumerGroupDetailResponse;
import cl.duoc.eventomax.kafkaadmin.dto.ConsumerGroupSummaryResponse;
import cl.duoc.eventomax.kafkaadmin.dto.TopicOperationResponse;
import cl.duoc.eventomax.kafkaadmin.dto.TopicResponse;
import cl.duoc.eventomax.kafkaadmin.dto.TopicSummaryResponse;
import cl.duoc.eventomax.kafkaadmin.service.KafkaAdminService;
import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=localhost:9092",
        "ENTRA_ISSUER_URI=https://issuer.invalid",
        "ENTRA_AUDIENCE=api://eventomax"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class KafkaAdminHttpTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AdminClient adminClient;

    @MockitoBean
    KafkaAdminService kafkaAdminService;

    @MockitoBean
    JwtDecoder decoder;

    static final String BASE = "/api/kafka-admin";

    @BeforeEach
    void setupAdminAuth() {
        identity(List.of("Admin"), "access_as_user");
    }

    void identity(List<String> roles, String scope) {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuer("https://issuer.invalid")
                .audience(List.of("api://eventomax"))
                .subject("test-admin")
                .expiresAt(Instant.now().plusSeconds(120))
                .claim("roles", roles)
                .claim("scp", scope)
                .build());
    }

    @Test
    void withoutJwtIs401() throws Exception {
        mvc.perform(get(BASE + "/topics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidJwtIs401() throws Exception {
        when(decoder.decode("test-token")).thenThrow(new BadJwtException("invalid test token"));
        mvc.perform(get(BASE + "/topics")
                .header("Authorization", "Bearer test-token"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Productor", "Organizador", "Auditor", "Unknown"})
    void nonAdminRoleIs403(String role) throws Exception {
        identity(List.of(role), "access_as_user");
        mvc.perform(get(BASE + "/topics")
                .header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListTopics() throws Exception {
        when(kafkaAdminService.listTopics()).thenReturn(List.of(
                new TopicSummaryResponse("productions.events", false, 3),
                new TopicSummaryResponse("audit.timeline", false, 3)
        ));

        mvc.perform(get(BASE + "/topics")
                .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("productions.events"))
                .andExpect(jsonPath("$[0].partitionsCount").value(3));
    }

    @Test
    void adminCanGetTopicDetail() throws Exception {
        when(kafkaAdminService.getTopic("productions.events")).thenReturn(
                new TopicResponse("productions.events", 3, 3, Collections.emptyList(), Map.of("retention.ms", "604800000"))
        );

        mvc.perform(get(BASE + "/topics/productions.events")
                .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("productions.events"))
                .andExpect(jsonPath("$.replicationFactor").value(3))
                .andExpect(jsonPath("$.configs['retention.ms']").value("604800000"));
    }

    @Test
    void adminCanCreateTopic() throws Exception {
        when(kafkaAdminService.createTopic(any())).thenReturn(
                new TopicOperationResponse("test.topic", "CREATED", "OK")
        );

        String payload = """
                {
                  "name": "test.topic",
                  "partitions": 3,
                  "replicationFactor": 3
                }
                """;

        mvc.perform(post(BASE + "/topics")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.topic").value("test.topic"))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void adminCanUpdateTopicConfigs() throws Exception {
        when(kafkaAdminService.updateTopicConfigs(eq("productions.events"), any())).thenReturn(
                new TopicOperationResponse("productions.events", "CONFIG_UPDATED", "Configuraciones actualizadas")
        );

        String payload = """
                {
                  "configs": {
                    "retention.ms": "604800000",
                    "cleanup.policy": "delete"
                  }
                }
                """;

        mvc.perform(put(BASE + "/topics/productions.events/configs")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topic").value("productions.events"))
                .andExpect(jsonPath("$.status").value("CONFIG_UPDATED"));
    }

    @Test
    void adminCanUpdateTopicPartitions() throws Exception {
        when(kafkaAdminService.updateTopicPartitions(eq("productions.events"), any())).thenReturn(
                new TopicOperationResponse("productions.events", "PARTITIONS_UPDATED", "Particiones actualizadas a 6")
        );

        String payload = """
                {
                  "totalPartitions": 6
                }
                """;

        mvc.perform(put(BASE + "/topics/productions.events/partitions")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topic").value("productions.events"))
                .andExpect(jsonPath("$.status").value("PARTITIONS_UPDATED"));
    }

    @Test
    void adminCanListConsumerGroups() throws Exception {
        when(kafkaAdminService.listConsumerGroups()).thenReturn(List.of(
                new ConsumerGroupSummaryResponse("report-group", "STABLE", false),
                new ConsumerGroupSummaryResponse("audit-group", "STABLE", false)
        ));

        mvc.perform(get(BASE + "/consumer-groups")
                .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].groupId").value("report-group"))
                .andExpect(jsonPath("$[0].state").value("STABLE"));
    }

    @Test
    void adminCanGetConsumerGroupDetail() throws Exception {
        when(kafkaAdminService.getConsumerGroup("report-group")).thenReturn(
                new ConsumerGroupDetailResponse("report-group", "STABLE", "range", 1, List.of(
                        new ConsumerGroupDetailResponse.MemberInfoResponse("member-1", "client-1", "/127.0.0.1", List.of("productions.events-0"))
                ))
        );

        mvc.perform(get(BASE + "/consumer-groups/report-group")
                .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value("report-group"))
                .andExpect(jsonPath("$.state").value("STABLE"))
                .andExpect(jsonPath("$.members[0].clientId").value("client-1"));
    }

    @Test
    void invalidTopicNameIs400() throws Exception {
        String invalidPayload = """
                {
                  "name": "invalid topic with spaces!",
                  "partitions": 3,
                  "replicationFactor": 3
                }
                """;

        mvc.perform(post(BASE + "/topics")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidPayload))
                .andExpect(status().isBadRequest());
    }
}
