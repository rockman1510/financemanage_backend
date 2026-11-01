CREATE SCHEMA IF NOT EXISTS budget;

CREATE TABLE budget.budget (
    id                 UUID PRIMARY KEY,
    owner_id           UUID           NOT NULL,
    name               VARCHAR(60)    NOT NULL,
    amount             NUMERIC(19, 4) NOT NULL,
    currency           VARCHAR(3)     NOT NULL,
    wallet_id          UUID,
    period             VARCHAR(10)    NOT NULL,
    start_date         DATE           NOT NULL,
    end_date           DATE,
    repeat_period      BOOLEAN        NOT NULL,
    alert_period_start DATE,
    alert_level        INTEGER        NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    version            BIGINT         NOT NULL
);

CREATE INDEX idx_budget_owner ON budget.budget (owner_id);

-- empty for a budget covering every expense category
CREATE TABLE budget.budget_category (
    budget_id   UUID NOT NULL REFERENCES budget.budget (id) ON DELETE CASCADE,
    category_id UUID NOT NULL,
    PRIMARY KEY (budget_id, category_id)
);
