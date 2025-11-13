
--liquibase formatted sql

--changeset odin:20240524155014_create_service_cache_table_for_cache
CREATE TABLE IF NOT EXISTS service_cache (
    id INT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL,
    description TEXT,
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    tags JSON,
    labels TEXT,
    org_id INT NOT NULL,
    CONSTRAINT unique_name_version_orgid UNIQUE (name, version, org_id)
);
