-- Index review (phase 8): the nightly cleanup deletes by expiry; foreign keys used by ON DELETE CASCADE
CREATE INDEX idx_refresh_token_expires ON identity.refresh_token (expires_at);
CREATE INDEX idx_verification_code_expires ON identity.verification_code (expires_at);
CREATE INDEX idx_login_challenge_expires ON identity.login_challenge (expires_at);
CREATE INDEX idx_login_challenge_user ON identity.login_challenge (user_id);
