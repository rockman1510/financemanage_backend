CREATE SCHEMA IF NOT EXISTS deposit;

CREATE TABLE deposit.deposit_account (
    id                      UUID PRIMARY KEY,
    owner_id                UUID           NOT NULL,
    institution_name        VARCHAR(120)   NOT NULL,
    account_number          VARCHAR(64),
    type                    VARCHAR(20)    NOT NULL,
    principal_amount        NUMERIC(19, 4) NOT NULL,
    currency                VARCHAR(3)     NOT NULL,
    annual_rate             NUMERIC(9, 4)  NOT NULL,
    early_withdrawal_rate   NUMERIC(9, 4)  NOT NULL,
    term_months             INTEGER,
    open_date               DATE           NOT NULL,
    maturity_date           DATE,
    interest_payment_method VARCHAR(20)    NOT NULL,
    rollover_policy         VARCHAR(30)    NOT NULL,
    calculation_method      VARCHAR(30)    NOT NULL,
    status                  VARCHAR(20)    NOT NULL,
    note                    VARCHAR(500),
    settled_on              DATE,
    settled_amount          NUMERIC(19, 4),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    version                 BIGINT         NOT NULL
);

CREATE INDEX idx_deposit_owner_status ON deposit.deposit_account (owner_id, status);
CREATE INDEX idx_deposit_status_maturity ON deposit.deposit_account (status, maturity_date);

CREATE TABLE deposit.deposit_transaction (
    id          UUID PRIMARY KEY,
    deposit_id  UUID           NOT NULL REFERENCES deposit.deposit_account (id) ON DELETE CASCADE,
    type        VARCHAR(20)    NOT NULL,
    amount      NUMERIC(19, 4) NOT NULL,
    occurred_on DATE           NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_deposit_tx_deposit ON deposit.deposit_transaction (deposit_id);
