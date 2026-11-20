ALTER TABLE sku ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX idx_sku_product_active ON sku(product_id,active);
INSERT INTO system_guard(scope_key) VALUES('ADMIN');
INSERT INTO system_guard(scope_key) VALUES('ISSUE');
