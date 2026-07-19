package com.dts.identity.repository;

import com.dts.identity.entity.OutboxEvent;
import com.dts.identity.entity.OutboxEvent.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Lấy batch PENDING events, sắp xếp theo thời gian tạo (FIFO).
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'PENDING' ORDER BY e.createdAt ASC")
    List<OutboxEvent> findPendingEvents(Pageable pageable);

    /**
     * Đánh dấu 1 event là PROCESSED.
     */
    @Modifying
    @Query("UPDATE OutboxEvent e SET e.status = 'PROCESSED', e.processedAt = :now WHERE e.id = :id")
    int markProcessed(@Param("id") UUID id, @Param("now") Instant now);

    /**
     * Tăng retry count và đánh dấu FAILED nếu vượt max_retries.
     */
    @Modifying
    @Query("UPDATE OutboxEvent e SET e.retryCount = e.retryCount + 1, " +
           "e.status = CASE WHEN e.retryCount + 1 >= e.maxRetries THEN 'FAILED' ELSE 'PENDING' END, " +
           "e.lastError = :error WHERE e.id = :id")
    int incrementRetry(@Param("id") UUID id, @Param("error") String error);

    /**
     * Dọn dẹp các event PROCESSED cũ hơn N ngày.
     */
    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status = 'PROCESSED' AND e.processedAt < :before")
    int deleteProcessedOlderThan(@Param("before") Instant before);

    /**
     * Đếm số event PENDING (cho monitoring).
     */
    long countByStatus(OutboxStatus status);
}
