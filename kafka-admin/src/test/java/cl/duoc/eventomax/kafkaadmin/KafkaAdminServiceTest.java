package cl.duoc.eventomax.kafkaadmin;

import cl.duoc.eventomax.kafkaadmin.service.KafkaAdminService;
import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

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
}

