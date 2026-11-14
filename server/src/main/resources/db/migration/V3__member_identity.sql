CREATE TABLE member_identity (
  member_id BIGINT PRIMARY KEY,
  state VARCHAR(20) NOT NULL,
  credential_hash VARCHAR(64) NOT NULL,
  id_number_hash VARCHAR(64) NOT NULL,
  verified_id_hash VARCHAR(64) UNIQUE,
  name_ciphertext VARCHAR(1000) NOT NULL,
  id_number_ciphertext VARCHAR(1000) NOT NULL,
  name_masked VARCHAR(80) NOT NULL,
  id_number_masked VARCHAR(40) NOT NULL,
  provider VARCHAR(40) NOT NULL,
  provider_request_id VARCHAR(160),
  development BOOLEAN NOT NULL DEFAULT FALSE,
  verified_at BIGINT,
  rejection_reason VARCHAR(240) NOT NULL,
  active_attempt_id VARCHAR(40) NOT NULL,
  last_attempt_at BIGINT NOT NULL,
  attempt_day BIGINT NOT NULL,
  attempt_count INT NOT NULL,
  consent_version VARCHAR(40) NOT NULL,
  consent_at BIGINT NOT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  CHECK (state IN ('PENDING','VERIFIED','REJECTED')),
  CHECK (attempt_count >= 1)
);

CREATE TABLE identity_attempt (
  id VARCHAR(40) PRIMARY KEY,
  member_id BIGINT NOT NULL,
  state VARCHAR(20) NOT NULL,
  provider VARCHAR(40) NOT NULL,
  provider_request_id VARCHAR(160),
  development BOOLEAN NOT NULL DEFAULT FALSE,
  reason VARCHAR(240) NOT NULL,
  created_at BIGINT NOT NULL,
  finished_at BIGINT,
  CHECK (state IN ('PENDING','VERIFIED','REJECTED','UNKNOWN','STALE'))
);
CREATE INDEX identity_attempt_member_created ON identity_attempt(member_id,created_at);
