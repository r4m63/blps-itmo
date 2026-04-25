BEGIN;

DROP TABLE IF EXISTS processed_messages;
DROP TABLE IF EXISTS outbox_events;
DROP TABLE IF EXISTS claim_timeline;
DROP TABLE IF EXISTS claims;

DROP TYPE IF EXISTS outbox_status;
DROP TYPE IF EXISTS outbox_event_type;
DROP TYPE IF EXISTS claim_status;

COMMIT;
