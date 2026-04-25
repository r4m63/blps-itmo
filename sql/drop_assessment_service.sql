BEGIN;

DROP TABLE IF EXISTS processed_messages;
DROP TABLE IF EXISTS outbox_events;
DROP TABLE IF EXISTS assessment_jobs;

DROP TYPE IF EXISTS outbox_status;
DROP TYPE IF EXISTS outbox_event_type;
DROP TYPE IF EXISTS assessment_job_status;

COMMIT;
