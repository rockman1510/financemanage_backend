-- optional wallet the principal came from / the settled amount went to (recorded by the ledger)
ALTER TABLE deposit.deposit_account ADD COLUMN funding_wallet_id UUID;
ALTER TABLE deposit.deposit_account ADD COLUMN funding_status VARCHAR(10);
ALTER TABLE deposit.deposit_account ADD COLUMN payout_wallet_id UUID;
ALTER TABLE deposit.deposit_account ADD COLUMN payout_status VARCHAR(10);
