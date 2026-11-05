package cl.duoc.eventomax.kafkaadmin.controller;

import cl.duoc.eventomax.kafkaadmin.dto.ConsumerGroupDetailResponse;
import cl.duoc.eventomax.kafkaadmin.dto.ConsumerGroupSummaryResponse;
import cl.duoc.eventomax.kafkaadmin.dto.TopicConfigUpdateRequest;
import cl.duoc.eventomax.kafkaadmin.dto.TopicOperationResponse;
import cl.duoc.eventomax.kafkaadmin.dto.TopicPartitionsUpdateRequest;
import cl.duoc.eventomax.kafkaadmin.dto.TopicRequest;
import cl.duoc.eventomax.kafkaadmin.dto.TopicResponse;
import cl.duoc.eventomax.kafkaadmin.dto.TopicSummaryResponse;
import cl.duoc.eventomax.kafkaadmin.service.KafkaAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/kafka-admin")
public class KafkaAdminController {

    private final KafkaAdminService service;

    public KafkaAdminController(KafkaAdminService service) {
        this.service = service;
    }

    @GetMapping("/topics")
    public List<TopicSummaryResponse> listTopics() {
        return service.listTopics();
    }

    @GetMapping("/topics/{name}")
    public TopicResponse getTopic(@PathVariable String name) {
        return service.getTopic(name);
    }

    @PostMapping("/topics")
    @ResponseStatus(HttpStatus.CREATED)
    public TopicOperationResponse createTopic(@Valid @RequestBody TopicRequest request) {
        return service.createTopic(request);
    }

    @PutMapping("/topics/{name}/configs")
    public TopicOperationResponse updateTopicConfigs(
            @PathVariable String name,
            @Valid @RequestBody TopicConfigUpdateRequest request) {
        return service.updateTopicConfigs(name, request);
    }

    @PutMapping("/topics/{name}/partitions")
    public TopicOperationResponse updateTopicPartitions(
            @PathVariable String name,
            @Valid @RequestBody TopicPartitionsUpdateRequest request) {
        return service.updateTopicPartitions(name, request);
    }

    @DeleteMapping("/topics/{name}")
    public TopicOperationResponse deleteTopic(@PathVariable String name) {
        return service.deleteTopic(name);
    }

    @GetMapping("/consumer-groups")
    public List<ConsumerGroupSummaryResponse> listConsumerGroups() {
        return service.listConsumerGroups();
    }

    @GetMapping("/consumer-groups/{groupId}")
    public ConsumerGroupDetailResponse getConsumerGroup(@PathVariable String groupId) {
        return service.getConsumerGroup(groupId);
    }

    @DeleteMapping("/consumer-groups/{groupId}")
    public TopicOperationResponse deleteConsumerGroup(@PathVariable String groupId) {
        return service.deleteConsumerGroup(groupId);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }
}
