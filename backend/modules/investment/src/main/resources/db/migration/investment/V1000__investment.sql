CREATE SCHEMA IF NOT EXISTS investment;

CREATE TABLE investment.asset (
    id         UUID PRIMARY KEY,
    owner_id   UUID           NOT NULL,
    name       VARCHAR(60)    NOT NULL,
    symbol     VARCHAR(20),
    type       VARCHAR(15)    NOT NULL,
    currency   VARCHAR(3)     NOT NULL,
    note       VARCHAR(300),
    quantity   NUMERIC(24, 8) NOT NULL,
    cost_basis NUMERIC(19, 4) NOT NULL,
    realized   NUMERIC(19, 4) NOT NULL,
    last_price NUMERIC(24, 8),
    price_date DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version    BIGINT         NOT NULL
);

CREATE INDEX idx_investment_asset_owner ON investment.asset (owner_id);

CREATE TABLE investment.trade (
    id             UUID PRIMARY KEY,
    asset_id       UUID           NOT NULL REFERENCES investment.asset (id) ON DELETE CASCADE,
    type           VARCHAR(10)    NOT NULL,
    quantity       NUMERIC(24, 8),
    price          NUMERIC(24, 8),
    fee            NUMERIC(19, 4) NOT NULL,
    cash           NUMERIC(19, 4) NOT NULL,
    realized       NUMERIC(19, 4),
    trade_date     DATE           NOT NULL,
    wallet_id      UUID,
    cash_status    VARCHAR(10)    NOT NULL,
    transaction_id UUID,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_investment_trade_asset ON investment.trade (asset_id);
