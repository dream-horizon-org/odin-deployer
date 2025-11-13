--liquibase formatted sql

--changeset odin:20240716155014_rename_service_task_column
ALTER TABLE service_task RENAME COLUMN config_hash TO service_config_hash;

ALTER TABLE service_validate_task RENAME COLUMN config_hash TO service_config_hash;
