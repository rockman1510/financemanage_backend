-- the same fact (e.g. one bill due date) is notified once, even if a job runs twice
ALTER TABLE notification.notification ADD COLUMN dedup_key VARCHAR(150);
CREATE UNIQUE INDEX uq_notification_dedup ON notification.notification (owner_id, dedup_key);

CREATE TABLE notification.push_device (
    token         VARCHAR(200) PRIMARY KEY,
    owner_id      UUID         NOT NULL,
    platform      VARCHAR(10),
    device_name   VARCHAR(100),
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_push_device_owner ON notification.push_device (owner_id);

CREATE TABLE notification.preferences (
    owner_id               UUID PRIMARY KEY,
    disabled_types         VARCHAR(500) NOT NULL,
    daily_reminder_enabled BOOLEAN      NOT NULL,
    daily_reminder_time    TIME         NOT NULL,
    quiet_start            TIME,
    quiet_end              TIME,
    time_zone              VARCHAR(40)  NOT NULL,
    last_recorded_on       DATE,
    last_reminder_on       DATE
);
