--liquibase formatted sql

--changeset odin:000_core_data context:seed
INSERT INTO action (name, created_by, updated_by)
VALUES ('DEPLOY', 1, 1) AS new
ON DUPLICATE KEY
UPDATE name = new.name, created_by = new.created_by, updated_by = new.updated_by;

INSERT INTO action (name, created_by, updated_by)
VALUES ('UNDEPLOY', 1, 1) AS new
ON DUPLICATE KEY
UPDATE name = new.name, created_by = new.created_by, updated_by = new.updated_by;

INSERT INTO action (name, created_by, updated_by)
VALUES ('OPERATE', 1, 1) AS new
ON DUPLICATE KEY
UPDATE name = new.name, created_by = new.created_by, updated_by = new.updated_by;

INSERT INTO action (name, created_by, updated_by)
VALUES ('VALIDATE', 1, 1) AS new
ON DUPLICATE KEY
UPDATE name = new.name, created_by = new.created_by, updated_by = new.updated_by;

INSERT INTO action (name, created_by, updated_by)
VALUES ('CREATE_ENVIRONMENT', 1, 1) AS new
ON DUPLICATE KEY
UPDATE name = new.name, created_by = new.created_by, updated_by = new.updated_by;

INSERT INTO action (name, created_by, updated_by)
VALUES ('DELETE_ENVIRONMENT', 1, 1) AS new
ON DUPLICATE KEY
UPDATE name = new.name, created_by = new.created_by, updated_by = new.updated_by;

INSERT INTO action (name, created_by, updated_by)
VALUES ('HEALTHCHECK', 1, 1) AS new
ON DUPLICATE KEY
UPDATE name = new.name, created_by = new.created_by, updated_by = new.updated_by;
