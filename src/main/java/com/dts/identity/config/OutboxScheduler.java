package com.dts.identity.config;

import com.dts.identity.entity.OutboxEvent;
import com.dts.identity.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Outbox Scheduler:
 * - Quét bảng outbox_events định kỳ (mỗi {@value #pollIntervalMs}ms)
 * - Lấy batch PENDING events theo FIFO
 * - Parse JSON payload → Map, đẩy vào Kafka topic tương ứng
 * - Thành công → PROCESSED | Thất bại → retry / FAILED
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${outbox.poll-interval-ms:5000}")
    private long pollIntervalMs;

    @Value("${outbox.batch-size:50}")
    private int batchSize;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    /**
     * Quét và xử lý outbox events định kỳ.
     * fixedDelayString đảm bảo khoảng cách giữa các lần chạy (không chồng lấn).
     */
    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:5000}")
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository
                .findPendingEvents(PageRequest.of(0, batchSize));

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox events", pendingEvents.size());
        int processed = 0;
        int failed = 0;

        for (OutboxEvent event : pendingEvents) {
            try {
                // Parse JSON payload back to Map để tránh double-encoding
                // khi JsonSerializer của Kafka serialize lần nữa
                Map<String, Object> value = objectMapper.readValue(event.getPayload(), MAP_TYPE);

                CompletableFuture<SendResult<String, Object>> future =
                        kafkaTemplate.send(event.getTopic(), event.getEventType(), value);

                SendResult<String, Object> result = future.get();
                log.debug("Outbox event sent: topic={}, key={}, offset={}",
                        event.getTopic(), event.getEventType(),
                        result.getRecordMetadata().offset());

                outboxEventRepository.markProcessed(event.getId(), Instant.now());
                processed++;

            } catch (Exception e) {
                log.error("Failed to send outbox event: id={}, topic={}, type={}, error={}",
                        event.getId(), event.getTopic(), event.getEventType(), e.getMessage());

                String errorMsg = e.getMessage() != null
                        ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 1000))
                        : "Unknown error";
                outboxEventRepository.incrementRetry(event.getId(), errorMsg);
                failed++;
            }
        }

        if (processed > 0 || failed > 0) {
            log.info("Outbox batch complete: processed={}, failed={}", processed, failed);
        }
    }

    /**
     * Dọn dẹp event PROCESSED > 7 ngày (chạy mỗi giờ).
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupProcessedEvents() {
        Instant before = Instant.now().minusSeconds(7 * 24 * 3600); // 7 days
        int deleted = outboxEventRepository.deleteProcessedOlderThan(before);
        if (deleted > 0) {
            log.info("Cleaned {} processed outbox events older than 7 days", deleted);
        }
    }
}
