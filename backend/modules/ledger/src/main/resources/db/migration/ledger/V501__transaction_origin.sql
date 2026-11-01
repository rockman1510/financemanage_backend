-- Module and record that requested a transaction (e.g. BILL + payment id); NULL for the user's own entries
ALTER TABLE ledger.money_transaction ADD COLUMN source VARCHAR(20);
ALTER TABLE ledger.money_transaction ADD COLUMN source_id UUID;
CREATE INDEX idx_transaction_source ON ledger.money_transaction (source, source_id);
