CREATE INDEX idx_product_status_page ON product(status,created_at,id);
CREATE INDEX idx_refund_page ON refund(created_at,id);
CREATE INDEX idx_withdrawal_page ON withdrawal(created_at,id);
CREATE INDEX idx_commission_page ON commission(created_at,id);
CREATE INDEX idx_audit_page ON audit(created_at,id);
CREATE INDEX idx_issue_page ON issue(created_at,id);
CREATE INDEX idx_settlement_page ON settlement(created_at,id);
CREATE INDEX idx_member_page ON member(created_at,id);
