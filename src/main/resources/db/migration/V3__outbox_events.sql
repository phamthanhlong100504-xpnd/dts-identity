-- ============================================================
-- V3__outbox_events.sql
-- Outbox Pattern: đảm bảo giao dịch DB và Kafka message đồng bộ
-- ============================================================

CREATE TABLE outbox_events (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    VARCHAR(100)    NOT NULL,              -- ID đối tượng liên quan (userId, ...)
    aggregate_type  VARCHAR(100)    NOT NULL DEFAULT 'USER',-- Loại đối tượng
    event_type      VARCHAR(100)    NOT NULL,              -- Loại sự kiện: password-reset-requested, ...
    topic           VARCHAR(100)    NOT NULL,              -- Kafka topic: user-events / auth-events
    payload         JSONB           NOT NULL DEFAULT '{}', -- Nội dung event
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED')),
    retry_count     INT             NOT NULL DEFAULT 0,
    max_retries     INT             NOT NULL DEFAULT 5,
    last_error      TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ
);

-- Index tối ưu cho OutboxScheduler quét PENDING events
CREATE INDEX idx_outbox_status_created ON outbox_events(status, created_at)
    WHERE status = 'PENDING';

-- Index tra cứu theo aggregate_id
CREATE INDEX idx_outbox_aggregate ON outbox_events(aggregate_id, aggregate_type);
