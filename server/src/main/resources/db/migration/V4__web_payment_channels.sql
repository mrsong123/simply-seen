ALTER TABLE shop_order ADD COLUMN payment_channel VARCHAR(20) NOT NULL DEFAULT 'UNSELECTED';
ALTER TABLE shop_order ADD COLUMN payment_payer_openid VARCHAR(128);
ALTER TABLE shop_order ADD COLUMN payment_app_id VARCHAR(80);
UPDATE shop_order SET payment_channel='MINI_PROGRAM' WHERE payment_state<>'INIT';

CREATE TABLE payment_session (
  order_id VARCHAR(40) PRIMARY KEY,
  channel VARCHAR(20) NOT NULL,
  attempt_id VARCHAR(40) NOT NULL,
  state VARCHAR(20) NOT NULL,
  response_payload TEXT,
  lease_until BIGINT NOT NULL,
  expires_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  CHECK (channel IN ('MINI_PROGRAM','NATIVE','H5')),
  CHECK (state IN ('CREATING','READY','UNKNOWN','FAILED'))
);
