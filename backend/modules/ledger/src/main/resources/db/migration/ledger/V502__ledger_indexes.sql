-- Index review (phase 8): account deletion finds the owner's attachments (to delete their files)
CREATE INDEX idx_attachment_owner ON ledger.attachment (owner_id);
