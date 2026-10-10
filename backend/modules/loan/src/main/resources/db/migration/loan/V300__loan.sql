CREATE SCHEMA IF NOT EXISTS loan;

CREATE TABLE loan.loan (
    id                       UUID PRIMARY KEY,
    owner_id                 UUID           NOT NULL,
    counterparty_name        VARCHAR(120)   NOT NULL,
    direction                VARCHAR(10)    NOT NULL,
    type                     VARCHAR(20)    NOT NULL,
    principal_amount         NUMERIC(19, 4) NOT NULL,
    currency                 VARCHAR(3)     NOT NULL,
    term_months              INTEGER        NOT NULL,
    start_date               DATE           NOT NULL,
    repayment_method         VARCHAR(20)    NOT NULL,
    early_repayment_fee_rate NUMERIC(9, 4)  NOT NULL,
    status                   VARCHAR(20)    NOT NULL,
    note                     VARCHAR(500),
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    version                  BIGINT         NOT NULL
);

CREATE INDEX idx_loan_owner_status ON loan.loan (owner_id, status);

CREATE TABLE loan.loan_rate_period (
    loan_id        UUID          NOT NULL REFERENCES loan.loan (id) ON DELETE CASCADE,
    effective_from DATE          NOT NULL,
    annual_rate    NUMERIC(9, 4) NOT NULL
);

CREATE INDEX idx_loan_rate_loan ON loan.loan_rate_period (loan_id);

CREATE TABLE loan.loan_installment (
    id             UUID PRIMARY KEY,
    loan_id        UUID           NOT NULL REFERENCES loan.loan (id) ON DELETE CASCADE,
    period_no      INTEGER        NOT NULL,
    due_date       DATE           NOT NULL,
    principal_due  NUMERIC(19, 4) NOT NULL,
    interest_due   NUMERIC(19, 4) NOT NULL,
    principal_paid NUMERIC(19, 4) NOT NULL,
    interest_paid  NUMERIC(19, 4) NOT NULL,
    settled        BOOLEAN        NOT NULL
);

CREATE INDEX idx_installment_loan ON loan.loan_installment (loan_id);
CREATE INDEX idx_installment_due_unsettled ON loan.loan_installment (due_date, settled);

CREATE TABLE loan.loan_repayment (
    id                UUID PRIMARY KEY,
    loan_id           UUID           NOT NULL REFERENCES loan.loan (id) ON DELETE CASCADE,
    type              VARCHAR(20)    NOT NULL,
    paid_on           DATE           NOT NULL,
    amount            NUMERIC(19, 4) NOT NULL,
    principal_portion NUMERIC(19, 4) NOT NULL,
    interest_portion  NUMERIC(19, 4) NOT NULL,
    fee_portion       NUMERIC(19, 4) NOT NULL,
    idempotency_key   VARCHAR(100),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_repayment_idempotency UNIQUE (loan_id, idempotency_key)
);
