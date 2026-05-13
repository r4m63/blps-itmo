BEGIN;

DROP TABLE IF EXISTS claim_attachments;
DROP TABLE IF EXISTS claim_status_history;
DROP TABLE IF EXISTS claim_messages;
DROP TABLE IF EXISTS claims;

DROP TYPE IF EXISTS attachmentpurpose;
DROP TYPE IF EXISTS commenttype;
DROP TYPE IF EXISTS claimstatus;

COMMIT;
