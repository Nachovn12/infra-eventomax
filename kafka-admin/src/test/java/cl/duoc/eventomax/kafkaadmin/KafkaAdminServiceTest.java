package cl.duoc.eventomax.kafkaadmin;

import cl.duoc.eventomax.kafkaadmin.dto.ConsumerGroupDetailResponse;
import cl.duoc.eventomax.kafkaadmin.dto.TopicConfigUpdateRequest;
import cl.duoc.eventomax.kafkaadmin.dto.TopicOperationResponse;
import cl.duoc.eventomax.kafkaadmin.dto.TopicPartitionsUpdateRequest;
import cl.duoc.eventomax.kafkaadmin.service.KafkaAdminService;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterConfigsResult;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.CreatePartitionsResult;
import org.apache.kafka.clients.admin.DescribeConsumerGroupsResult;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.ConsumerGroupState;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

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
            "audit-service-group",
            "audit-timeline-group",
            "audit-group-audit-retry"
    })
    void deletingProtectedConsumerGroupsThrowsException(String groupId) {
        assertThrows(IllegalArgumentException.class, () -> service.deleteConsumerGroup(groupId));
    }

    @Test
    void getConsumerGroupCalculatesOffsetsAndLag() {
        var tp0 = new TopicPartition("productions.events", 0);
        var tp1 = new TopicPartition("productions.events", 1);

        var desc = new ConsumerGroupDescription(
                "report-group",
                false,
                Collections.emptyList(),
                "range",
                ConsumerGroupState.STABLE,
                new Node(1, "kafka-1", 29092)
        );
        var describeResult = mock(DescribeConsumerGroupsResult.class);
        when(describeResult.all()).thenReturn(KafkaFuture.completedFuture(Map.of("report-group", desc)));
        when(adminClient.describeConsumerGroups(Collections.singletonList("report-group"))).thenReturn(describeResult);

        var offsetsResult = mock(ListConsumerGroupOffsetsResult.class);
        when(offsetsResult.partitionsToOffsetAndMetadata()).thenReturn(KafkaFuture.completedFuture(Map.of(
                tp0, new OffsetAndMetadata(42L),
                tp1, new OffsetAndMetadata(10L)
        )));
        when(adminClient.listConsumerGroupOffsets("report-group")).thenReturn(offsetsResult);

        var listOffsetsResult = mock(ListOffsetsResult.class);
        when(listOffsetsResult.all()).thenReturn(KafkaFuture.completedFuture(Map.of(
                tp0, new ListOffsetsResult.ListOffsetsResultInfo(47L, 0L, Optional.empty()),
                tp1, new ListOffsetsResult.ListOffsetsResultInfo(10L, 0L, Optional.empty())
        )));
        when(adminClient.listOffsets(anyMap())).thenReturn(listOffsetsResult);

        ConsumerGroupDetailResponse detail = service.getConsumerGroup("report-group");

        assertEquals("report-group", detail.groupId());
        assertEquals("STABLE", detail.state());
        assertEquals(5L, detail.totalLag());
        assertEquals(2, detail.partitionOffsets().size());
        assertEquals(42L, detail.partitionOffsets().get(0).currentOffset());
        assertEquals(47L, detail.partitionOffsets().get(0).endOffset());
        assertEquals(5L, detail.partitionOffsets().get(0).lag());
        assertEquals(0L, detail.partitionOffsets().get(1).lag());
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
