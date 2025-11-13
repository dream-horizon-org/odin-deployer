--liquibase formatted sql

--changeset odin:20241209094419_add_trace_id_column
ALTER TABLE service_task ADD COLUMN trace_id VARCHAR(255) DEFAULT '';

ALTER TABLE service_validate_task ADD COLUMN trace_id VARCHAR(255) DEFAULT '';

ALTER TABLE environment_task ADD COLUMN trace_id VARCHAR(255) DEFAULT '';
