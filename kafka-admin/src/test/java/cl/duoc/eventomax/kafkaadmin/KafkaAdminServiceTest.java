package cl.duoc.eventomax.kafkaadmin;

import cl.duoc.eventomax.kafkaadmin.dto.TopicConfigUpdateRequest;
import cl.duoc.eventomax.kafkaadmin.dto.TopicOperationResponse;
import cl.duoc.eventomax.kafkaadmin.dto.TopicPartitionsUpdateRequest;
import cl.duoc.eventomax.kafkaadmin.service.KafkaAdminService;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterConfigsResult;
import org.apache.kafka.clients.admin.CreatePartitionsResult;
import org.apache.kafka.common.KafkaFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

class KafkaAdminServiceTest {

    private AdminClient adminClient;
    private KafkaAdminService service;

    @BeforeEach
    void setUp() {
        adminClient = mock(AdminClient.class);
        service = new KafkaAdminService(adminClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "productions.events",
            "audit.timeline",
            "productions.events.report.DLT",
            "productions.events-audit-retry",
            "productions.events-audit-dlt"
    })
    void deletingProtectedTopicsThrowsException(String topic) {
        assertThrows(IllegalArgumentException.class, () -> service.deleteTopic(topic));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "report-group",
            "report-service-group",
            "audit-group",
            "audit-service-group"
    })
    void deletingProtectedConsumerGroupsThrowsException(String groupId) {
        assertThrows(IllegalArgumentException.class, () -> service.deleteConsumerGroup(groupId));
    }

    @Test
    void updateTopicConfigsWithAllowedKeysSucceeds() {
        AlterConfigsResult mockResult = mock(AlterConfigsResult.class);
        when(mockResult.all()).thenReturn(KafkaFuture.completedFuture(null));
        when(adminClient.incrementalAlterConfigs(anyMap())).thenReturn(mockResult);

        TopicConfigUpdateRequest request = new TopicConfigUpdateRequest(Map.of(
                "retention.ms", "86400000",
                "cleanup.policy", "compact,delete"
        ));

        TopicOperationResponse response = service.updateTopicConfigs("custom.topic", request);

        assertEquals("custom.topic", response.topic());
        assertEquals("CONFIG_UPDATED", response.status());
        verify(adminClient, times(1)).incrementalAlterConfigs(anyMap());
    }

    @Test
    void updateTopicConfigsWithForbiddenKeyThrowsException() {
        TopicConfigUpdateRequest request = new TopicConfigUpdateRequest(Map.of(
                "untrusted.broker.property", "true"
        ));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateTopicConfigs("custom.topic", request));
        verifyNoInteractions(adminClient);
    }

    @Test
    void updateTopicPartitionsSucceeds() {
        CreatePartitionsResult mockResult = mock(CreatePartitionsResult.class);
        when(mockResult.all()).thenReturn(KafkaFuture.completedFuture(null));
        when(adminClient.createPartitions(anyMap())).thenReturn(mockResult);

        TopicPartitionsUpdateRequest request = new TopicPartitionsUpdateRequest(6);
        TopicOperationResponse response = service.updateTopicPartitions("custom.topic", request);

        assertEquals("custom.topic", response.topic());
        assertEquals("PARTITIONS_UPDATED", response.status());
        verify(adminClient, times(1)).createPartitions(anyMap());
    }
}
