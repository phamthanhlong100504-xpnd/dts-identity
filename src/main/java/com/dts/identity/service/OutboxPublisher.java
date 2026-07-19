package com.dts.identity.service;

import com.dts.identity.entity.OutboxEvent;
import com.dts.identity.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Ghi event vào bảng outbox_events thay vì gọi Kafka trực tiếp.
 * OutboxScheduler sẽ quét và đẩy vào Kafka sau (đảm bảo transactional outbox).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Ghi 1 event vào outbox. Dùng REQUIRES_NEW để đảm bảo ghi nhận
     * ngay cả khi transaction cha rollback (tùy use case).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String topic, String eventType, String aggregateId,
                        String aggregateType, Map<String, Object> payload) {
        payload.put("eventType", eventType);
        payload.put("timestamp", System.currentTimeMillis());

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox payload for event: {}", eventType, e);
            throw new RuntimeException("Failed to serialize outbox payload", e);
        }

        OutboxEvent event = OutboxEvent.builder()
                .topic(topic)
                .eventType(eventType)
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .payload(payloadJson)
                .build();

        outboxEventRepository.save(event);
        log.debug("Outbox event saved: topic={}, type={}, aggregateId={}",
                topic, eventType, aggregateId);
    }
}
