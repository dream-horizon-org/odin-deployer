--liquibase formatted sql

--changeset odin:20250401003338_add_index_on_traceid
CREATE INDEX idx_trace_id
  ON service_task (trace_id);
