package com.dts.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publishes identity-related events to Kafka for downstream services
 * (Notification Service, Analytics Service, Learning History Service, etc.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishUserEvent(String eventType, Map<String, Object> payload) {
        payload.put("eventType", eventType);
        payload.put("timestamp", System.currentTimeMillis());
        kafkaTemplate.send("user-events", eventType, payload);
        log.debug("Published user event: type={}", eventType);
    }

    public void publishAuthEvent(String eventType, Map<String, Object> payload) {
        payload.put("eventType", eventType);
        payload.put("timestamp", System.currentTimeMillis());
        kafkaTemplate.send("auth-events", eventType, payload);
        log.debug("Published auth event: type={}", eventType);
    }
}
