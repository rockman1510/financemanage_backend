CREATE SCHEMA IF NOT EXISTS ledger;

CREATE TABLE ledger.wallet (
    id                  UUID PRIMARY KEY,
    owner_id            UUID           NOT NULL,
    name                VARCHAR(60)    NOT NULL,
    type                VARCHAR(20)    NOT NULL,
    currency            VARCHAR(3)     NOT NULL,
    initial_balance     NUMERIC(19, 4) NOT NULL,
    balance             NUMERIC(19, 4) NOT NULL,
    excluded_from_total BOOLEAN        NOT NULL,
    archived            BOOLEAN        NOT NULL,
    icon                VARCHAR(40),
    credit_limit        NUMERIC(19, 4),
    statement_day       INTEGER,
    payment_due_day     INTEGER,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    version             BIGINT         NOT NULL
);

CREATE INDEX idx_wallet_owner ON ledger.wallet (owner_id);

CREATE TABLE ledger.category (
    id         UUID PRIMARY KEY,
    owner_id   UUID        NOT NULL,
    name       VARCHAR(60) NOT NULL,
    type       VARCHAR(10) NOT NULL,
    parent_id  UUID REFERENCES ledger.category (id),
    icon       VARCHAR(40),
    code       VARCHAR(40),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_category_owner ON ledger.category (owner_id);
CREATE INDEX idx_category_parent ON ledger.category (parent_id);

-- one row per user whose default categories were created
CREATE TABLE ledger.category_seed (
    owner_id  UUID PRIMARY KEY,
    seeded_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE ledger.money_transaction (
    id                    UUID PRIMARY KEY,
    owner_id              UUID           NOT NULL,
    wallet_id             UUID           NOT NULL REFERENCES ledger.wallet (id),
    type                  VARCHAR(20)    NOT NULL,
    amount                NUMERIC(19, 4) NOT NULL,
    currency              VARCHAR(3)     NOT NULL,
    category_id           UUID REFERENCES ledger.category (id),
    occurred_on           DATE           NOT NULL,
    note                  VARCHAR(500),
    with_person           VARCHAR(120),
    exclude_from_report   BOOLEAN        NOT NULL,
    transfer_id           UUID,
    counterpart_wallet_id UUID,
    exchange_rate         NUMERIC(24, 10),
    recurring_id          UUID,
    idempotency_key       VARCHAR(150),
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    version               BIGINT         NOT NULL,
    CONSTRAINT uq_transaction_idempotency UNIQUE (owner_id, idempotency_key)
);

CREATE INDEX idx_transaction_owner_date ON ledger.money_transaction (owner_id, occurred_on, created_at);
CREATE INDEX idx_transaction_wallet ON ledger.money_transaction (wallet_id);
CREATE INDEX idx_transaction_category ON ledger.money_transaction (category_id);
CREATE INDEX idx_transaction_transfer ON ledger.money_transaction (transfer_id);

CREATE TABLE ledger.recurring_transaction (
    id              UUID PRIMARY KEY,
    owner_id        UUID           NOT NULL,
    wallet_id       UUID           NOT NULL REFERENCES ledger.wallet (id),
    category_id     UUID           NOT NULL REFERENCES ledger.category (id),
    type            VARCHAR(20)    NOT NULL,
    amount          NUMERIC(19, 4) NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    note            VARCHAR(500),
    with_person     VARCHAR(120),
    frequency       VARCHAR(10)    NOT NULL,
    repeat_interval INTEGER        NOT NULL,
    start_date      DATE           NOT NULL,
    end_date        DATE,
    generated_count INTEGER        NOT NULL,
    next_occurrence DATE,
    active          BOOLEAN        NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    version         BIGINT         NOT NULL
);

CREATE INDEX idx_recurring_owner ON ledger.recurring_transaction (owner_id);
CREATE INDEX idx_recurring_due ON ledger.recurring_transaction (active, next_occurrence);

CREATE TABLE ledger.attachment (
    id             UUID PRIMARY KEY,
    owner_id       UUID         NOT NULL,
    transaction_id UUID         NOT NULL REFERENCES ledger.money_transaction (id) ON DELETE CASCADE,
    file_name      VARCHAR(120) NOT NULL,
    content_type   VARCHAR(100) NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    storage_key    VARCHAR(200) NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_attachment_transaction ON ledger.attachment (transaction_id);
