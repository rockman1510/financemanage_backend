CREATE SCHEMA IF NOT EXISTS notification;

CREATE TABLE notification.notification (
    id             UUID PRIMARY KEY,
    owner_id       UUID         NOT NULL,
    type           VARCHAR(40)  NOT NULL,
    title          VARCHAR(200) NOT NULL,
    body           VARCHAR(1000) NOT NULL,
    reference_type VARCHAR(20),
    reference_id   UUID,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    read_at        TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_notification_owner_created ON notification.notification (owner_id, created_at DESC);
