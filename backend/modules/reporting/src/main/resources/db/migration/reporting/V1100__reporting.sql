CREATE SCHEMA IF NOT EXISTS reporting;

-- Read model of ledger transactions for reports (one row per transaction; deleted ones kept as tombstones)
CREATE TABLE reporting.entry (
    transaction_id UUID PRIMARY KEY,
    owner_id       UUID           NOT NULL,
    wallet_id      UUID           NOT NULL,
    type           VARCHAR(20)    NOT NULL,
    amount         NUMERIC(19, 4) NOT NULL,
    currency       VARCHAR(3)     NOT NULL,
    category_id    UUID,
    entry_date     DATE           NOT NULL,
    excluded       BOOLEAN        NOT NULL,
    source         VARCHAR(30),
    deleted        BOOLEAN        NOT NULL
);

CREATE INDEX idx_reporting_entry_owner_date ON reporting.entry (owner_id, entry_date);

CREATE TABLE reporting.built_owner (
    owner_id UUID PRIMARY KEY,
    built_at TIMESTAMP WITH TIME ZONE NOT NULL
);
