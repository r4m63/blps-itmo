BEGIN;

DROP TABLE IF EXISTS processed_messages;
DROP TABLE IF EXISTS outbox_events;
DROP TABLE IF EXISTS attachments;

DROP TYPE IF EXISTS outbox_status;
DROP TYPE IF EXISTS outbox_event_type;
DROP TYPE IF EXISTS attachment_status;

COMMIT;
