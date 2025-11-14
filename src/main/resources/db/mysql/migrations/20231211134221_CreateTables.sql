--liquibase formatted sql

--changeset odin:20231211134221_create_tables
CREATE TABLE IF NOT EXISTS environment
(
  id                        BIGINT                                                          NOT NULL AUTO_INCREMENT,
  version                   INT                                                             NOT NULL DEFAULT '1',
  org_id                    BIGINT                                                          NOT NULL,
  name                      VARCHAR(256)                                                    NOT NULL,
  is_active                 tinyint(1)                                                      NOT NULL DEFAULT '1',
  created_by                VARCHAR(50)                                                     NOT NULL,
  created_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP                             NOT NULL,
  updated_by                VARCHAR(50)                                                     NOT NULL,
  updated_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,

  PRIMARY KEY (id),
  INDEX (org_id, name)
);

CREATE TABLE IF NOT EXISTS action
(
  id                        BIGINT      NOT NULL AUTO_INCREMENT,
  name                      VARCHAR(50) NOT NULL,
  version                   INT         NOT NULL DEFAULT '1',
  created_by                VARCHAR(50)                                                     NOT NULL,
  created_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP                             NOT NULL,
  updated_by                VARCHAR(50)                                                     NOT NULL,
  updated_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY actions_name_uindex (name)
);


CREATE TABLE IF NOT EXISTS service_task
(
  id                        BIGINT                                                          NOT NULL AUTO_INCREMENT,
  action_id                 BIGINT                                                          NOT NULL,
  config                    JSON                                                            NOT NULL,
  service_config_hash       VARCHAR(256)                                                    NOT NULL,
  env_id                    BIGINT                                                          NOT NULL,
  name                      VARCHAR(256)                                                    NOT NULL,
  service_version           VARCHAR(100),
  status                    VARCHAR(50)                                                     NOT NULL,
  version                   INT DEFAULT '1'                                                 NOT NULL,
  trace_id                  VARCHAR(255)                                                    NOT NULL DEFAULT '',
  created_by                VARCHAR(50)                                                     NOT NULL,
  created_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP                             NOT NULL,
  updated_by                VARCHAR(50)                                                     NOT NULL,
  updated_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (id),
  FOREIGN KEY (env_id) REFERENCES environment (id),
  INDEX (env_id, status),
  INDEX (env_id, name),
  INDEX idx_trace_id (trace_id),
  CONSTRAINT unique_env_name_version UNIQUE (env_id, name, version)
);

CREATE TABLE IF NOT EXISTS component_task
(
  id                        BIGINT                                                          NOT NULL AUTO_INCREMENT,
  service_task_id           BIGINT                                                          NOT NULL,
  action_id                 BIGINT                                                          NOT NULL,
  component_name            VARCHAR(256)                                                    NOT NULL,
  status                    VARCHAR(20)                                                     NOT NULL,
  config                    JSON                                                            NOT NULL,
  config_hash               TEXT                                                            NOT NULL,
  version                   INT                                                             NOT NULL DEFAULT '1',
  response                  JSON                                                            NULL,
  service_account_snapshot  JSON                                                            NULL,
  created_by                VARCHAR(50)                                                     NOT NULL,
  created_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP                             NOT NULL,
  updated_by                VARCHAR(50)                                                     NOT NULL,
  updated_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,

  PRIMARY KEY (id),
  FOREIGN KEY (service_task_id) REFERENCES service_task (id),
  FOREIGN KEY (action_id) REFERENCES action (id),
  INDEX (component_name),
  INDEX service_task_component_idx(service_task_id, component_name)
);


CREATE TABLE IF NOT EXISTS environment_task
(
  id                        BIGINT                                                          NOT NULL AUTO_INCREMENT,
  version                   INT                                                             NOT NULL,
  env_id                    BIGINT                                                          NOT NULL,
  action_id                 BIGINT                                                          NOT NULL,
  status                    VARCHAR(40)                                                     NOT NULL,
  provider_account_name     VARCHAR(20)                                                     NOT NULL,
  service_accounts_snapshot JSON                                                            NOT NULL,
  response                  JSON                                                            NULL,
  trace_id                  VARCHAR(255)                                                    NOT NULL DEFAULT '',
  created_by                VARCHAR(50)                                                     NOT NULL,
  created_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP                             NOT NULL,
  updated_by                VARCHAR(50)                                                     NOT NULL,
  updated_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,

  PRIMARY KEY (id),
  FOREIGN KEY (env_id) REFERENCES environment (id),
  INDEX env (env_id, status)
);

CREATE TABLE IF NOT EXISTS service_validate_task
(
  id                        BIGINT                                                          NOT NULL AUTO_INCREMENT,
  config                    JSON                                                            NOT NULL,
  service_config_hash       VARCHAR(256)                                                    NOT NULL,
  name                      VARCHAR(256)                                                    NOT NULL,
  service_version           VARCHAR(100)                                                    NOT NULL,
  status                    VARCHAR(50)                                                     NOT NULL,
  version                   INT DEFAULT '1'                                                 NOT NULL,
  trace_id                  VARCHAR(255)                                                    NOT NULL DEFAULT '',
  created_by                VARCHAR(50)                                                     NOT NULL,
  created_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP                             NOT NULL,
  updated_by                VARCHAR(50)                                                     NOT NULL,
  updated_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (id),
  INDEX (status),
  INDEX (name)
);


CREATE TABLE IF NOT EXISTS component_validate_task
(
  id                        BIGINT                                                          NOT NULL AUTO_INCREMENT,
  service_validate_task_id  BIGINT                                                          NOT NULL,
  component_name            VARCHAR(256)                                                    NOT NULL,
  status                    VARCHAR(20)                                                     NOT NULL,
  config                    JSON                                                            NOT NULL,
  config_hash               TEXT                                                            NOT NULL,
  version                   INT                                                             NOT NULL DEFAULT '1',
  response                  JSON                                                            NULL,
  service_account_snapshot  JSON                                                            NULL,
  created_by                VARCHAR(50)                                                     NOT NULL,
  created_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP                             NOT NULL,
  updated_by                VARCHAR(50)                                                     NOT NULL,
  updated_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,

  PRIMARY KEY (id),
  FOREIGN KEY (service_validate_task_id) REFERENCES service_validate_task (id),
  INDEX (component_name),
  INDEX service_task_component_idx(service_validate_task_id, component_name)
);


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


CREATE TABLE IF NOT EXISTS auth_provider
(
  id                BIGINT                              NOT NULL AUTO_INCREMENT,
  org_id            BIGINT                              NOT NULL,
  created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  type              VARCHAR(50)                         NOT NULL,
  updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
  provider_details  JSON                                NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (org_id)
);
