CREATE TABLE member_phone (
  member_id BIGINT PRIMARY KEY,
  phone_hash VARCHAR(64) NOT NULL UNIQUE,
  phone_ciphertext VARCHAR(256) NOT NULL,
  phone_masked VARCHAR(32) NOT NULL,
  created_at BIGINT NOT NULL
);

CREATE TABLE sms_challenge (
  id VARCHAR(40) PRIMARY KEY,
  phone_hash VARCHAR(64) NOT NULL,
  purpose VARCHAR(20) NOT NULL,
  member_id BIGINT NOT NULL,
  code_hash VARCHAR(64) NOT NULL,
  state VARCHAR(20) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  expires_at BIGINT NOT NULL,
  created_at BIGINT NOT NULL,
  provider_request_id VARCHAR(160),
  development BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_sms_challenge_phone ON sms_challenge(phone_hash,purpose,member_id,state);
CREATE INDEX idx_sms_challenge_created ON sms_challenge(created_at);

CREATE TABLE sms_throttle (
  bucket VARCHAR(128) PRIMARY KEY,
  hits INT NOT NULL DEFAULT 0,
  expires_at BIGINT NOT NULL
);
CREATE INDEX idx_sms_throttle_expiry ON sms_throttle(expires_at);

CREATE TABLE sms_cooldown (
  phone_hash VARCHAR(64) PRIMARY KEY,
  next_allowed_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE account_security_lock (lock_key VARCHAR(64) PRIMARY KEY);
