-- Accounts created with Google sign-in have no password
ALTER TABLE identity.user_account ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE identity.user_account ADD COLUMN email_verified_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE identity.user_account ADD COLUMN google_subject VARCHAR(255);
ALTER TABLE identity.user_account ADD COLUMN default_currency VARCHAR(3) DEFAULT 'VND' NOT NULL;
ALTER TABLE identity.user_account ADD COLUMN locale VARCHAR(10) DEFAULT 'vi' NOT NULL;
ALTER TABLE identity.user_account ADD COLUMN time_zone VARCHAR(40) DEFAULT 'Asia/Ho_Chi_Minh' NOT NULL;
ALTER TABLE identity.user_account ADD COLUMN month_start_day INTEGER DEFAULT 1 NOT NULL;
ALTER TABLE identity.user_account ADD COLUMN failed_login_count INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE identity.user_account ADD COLUMN locked_until TIMESTAMP WITH TIME ZONE;
ALTER TABLE identity.user_account ADD COLUMN totp_secret VARCHAR(255);
ALTER TABLE identity.user_account ADD COLUMN totp_enabled BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE identity.user_account ADD COLUMN totp_last_step BIGINT;
CREATE UNIQUE INDEX uq_user_google_subject ON identity.user_account (google_subject);

-- A session (one signed-in device) keeps its id across refresh token rotations
ALTER TABLE identity.refresh_token ADD COLUMN session_id UUID;
ALTER TABLE identity.refresh_token ADD COLUMN device_name VARCHAR(100);
ALTER TABLE identity.refresh_token ADD COLUMN created_at TIMESTAMP WITH TIME ZONE;
UPDATE identity.refresh_token SET session_id = id, created_at = CURRENT_TIMESTAMP;
ALTER TABLE identity.refresh_token ALTER COLUMN session_id SET NOT NULL;
ALTER TABLE identity.refresh_token ALTER COLUMN created_at SET NOT NULL;
CREATE INDEX idx_refresh_token_session ON identity.refresh_token (session_id);

-- One-time codes sent by email (email verification, password reset); only hashes are stored
CREATE TABLE identity.verification_code (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES identity.user_account (id) ON DELETE CASCADE,
    purpose    VARCHAR(30) NOT NULL,
    code_hash  VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts   INTEGER     NOT NULL,
    used_at    TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_verification_code_user ON identity.verification_code (user_id, purpose);

-- Two-factor recovery codes, each usable once
CREATE TABLE identity.recovery_code (
    id        UUID PRIMARY KEY,
    user_id   UUID        NOT NULL REFERENCES identity.user_account (id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL,
    used_at   TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_recovery_code_user ON identity.recovery_code (user_id);

-- Pending second step of a sign-in when two-factor authentication is on
CREATE TABLE identity.login_challenge (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES identity.user_account (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    device_name VARCHAR(100),
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts    INTEGER      NOT NULL,
    used        BOOLEAN      NOT NULL
);
