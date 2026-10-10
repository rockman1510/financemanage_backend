CREATE SCHEMA IF NOT EXISTS identity;

CREATE TABLE identity.user_account (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE identity.refresh_token (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES identity.user_account (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked    BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_refresh_token_user ON identity.refresh_token (user_id);
