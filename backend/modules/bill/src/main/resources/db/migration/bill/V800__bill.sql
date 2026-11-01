CREATE SCHEMA IF NOT EXISTS bill;

CREATE TABLE bill.bill (
    id                 UUID PRIMARY KEY,
    owner_id           UUID           NOT NULL,
    name               VARCHAR(60)    NOT NULL,
    amount             NUMERIC(19, 4) NOT NULL,
    currency           VARCHAR(3)     NOT NULL,
    wallet_id          UUID           NOT NULL,
    category_id        UUID           NOT NULL,
    frequency          VARCHAR(10)    NOT NULL,
    repeat_interval    INTEGER        NOT NULL,
    first_due_date     DATE           NOT NULL,
    end_date           DATE,
    remind_days_before INTEGER        NOT NULL,
    note               VARCHAR(500),
    settled_count      INTEGER        NOT NULL,
    next_due_date      DATE,
    active             BOOLEAN        NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    version            BIGINT         NOT NULL
);

CREATE INDEX idx_bill_owner ON bill.bill (owner_id);
CREATE INDEX idx_bill_due ON bill.bill (active, next_due_date);

CREATE TABLE bill.bill_payment (
    id             UUID PRIMARY KEY,
    bill_id        UUID           NOT NULL REFERENCES bill.bill (id) ON DELETE CASCADE,
    occurrence     INTEGER        NOT NULL,
    due_date       DATE           NOT NULL,
    amount         NUMERIC(19, 4),
    wallet_id      UUID,
    paid_on        DATE,
    status         VARCHAR(10)    NOT NULL,
    transaction_id UUID,
    failure_reason VARCHAR(300),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_bill_payment_bill ON bill.bill_payment (bill_id);
