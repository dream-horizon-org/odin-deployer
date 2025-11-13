--liquibase formatted sql

--changeset odin:20240508194306_add_account_to_component_task
ALTER TABLE component_task ADD COLUMN service_account_snapshot JSON;

ALTER TABLE component_validate_task ADD COLUMN service_account_snapshot JSON;
