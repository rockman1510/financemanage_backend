CREATE SCHEMA IF NOT EXISTS currency;

-- owner_id NULL = global rate (from an ExchangeRateProvider), visible to every user
CREATE TABLE currency.exchange_rate (
    id             UUID PRIMARY KEY,
    owner_id       UUID,
    base_currency  VARCHAR(3)     NOT NULL,
    quote_currency VARCHAR(3)     NOT NULL,
    rate           NUMERIC(24, 10) NOT NULL,
    effective_date DATE           NOT NULL,
    source         VARCHAR(40)    NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_exchange_rate_pair ON currency.exchange_rate (base_currency, quote_currency, effective_date);
CREATE INDEX idx_exchange_rate_owner ON currency.exchange_rate (owner_id, effective_date);
