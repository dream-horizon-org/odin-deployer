--liquibase formatted sql

--changeset odin:20250129094419_add_service_task_constraint
UPDATE service_task SET version = id;

ALTER TABLE service_task ADD CONSTRAINT unique_env_name_version UNIQUE (env_id, name, version);
