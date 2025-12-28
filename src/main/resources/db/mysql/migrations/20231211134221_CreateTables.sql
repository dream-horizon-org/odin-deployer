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

CREATE TABLE IF NOT EXISTS environment
(
  id                        BIGINT                                                          NOT NULL AUTO_INCREMENT,
  org_id                    BIGINT                                                          NOT NULL,
  name                      VARCHAR(256)                                                    NOT NULL,
  is_active                 TINYINT(1)                                                      NOT NULL DEFAULT '1',
  created_by                VARCHAR(50)                                                     NOT NULL,
  created_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP                             NOT NULL,
  updated_by                VARCHAR(50)                                                     NOT NULL,
  updated_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (id),
  INDEX (org_id, name)
);

CREATE TABLE IF NOT EXISTS environment_account
(
  id                        BIGINT                                                          NOT NULL AUTO_INCREMENT,
  environment_id            BIGINT                                                          NOT NULL,
  status                    VARCHAR(20)                                                     NOT NULL,
  action                    VARCHAR(50)                                                     NOT NULL,
  account_data             JSON                                                             NULL,
  account_name              VARCHAR(50)                                                     NOT NULL,
  created_by                VARCHAR(50)                                                     NOT NULL,
  created_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP                             NOT NULL,
  updated_by                VARCHAR(50)                                                     NOT NULL,
  updated_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
  FOREIGN KEY (environment_id) REFERENCES environment (id),
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS environment_service
(
  id                        BIGINT                                                          NOT NULL AUTO_INCREMENT,
  action                    VARCHAR(50)                                                     NOT NULL,
  config                    JSON                                                            NOT NULL,
  environment_id            BIGINT                                                          NOT NULL,
  name                      VARCHAR(256)                                                    NOT NULL,
  status                    VARCHAR(50)                                                     NOT NULL,
  created_by                VARCHAR(50)                                                     NOT NULL,
  created_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP                             NOT NULL,
  updated_by                VARCHAR(50)                                                     NOT NULL,
  updated_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (id),
  FOREIGN KEY (environment_id) REFERENCES environment (id),
  INDEX (environment_id, status),
  UNIQUE KEY unique_env_name (environment_id, name)
);

CREATE TABLE IF NOT EXISTS environment_service_component
(
  id                        BIGINT                                                          NOT NULL AUTO_INCREMENT,
  environment_service_id    BIGINT                                                          NOT NULL,
  action                    VARCHAR(50)                                                     NOT NULL,
  name                      VARCHAR(256)                                                    NOT NULL,
  status                    VARCHAR(20)                                                     NOT NULL,
  config                    JSON                                                            NOT NULL,
  account_data              JSON                                                            NULL,
  created_by                VARCHAR(50)                                                     NOT NULL,
  created_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP                             NOT NULL,
  updated_by                VARCHAR(50)                                                     NOT NULL,
  updated_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (id),
  FOREIGN KEY (environment_service_id) REFERENCES environment_service (id),
  INDEX (name),
  INDEX service_component_idx(environment_service_id, name),
  UNIQUE KEY (environment_service_id, name)
);

CREATE TABLE IF NOT EXISTS environment_lock
(
  id            BIGINT NOT NULL AUTO_INCREMENT,
  environment_id        BIGINT NOT NULL,
  shared_count  INT NOT NULL DEFAULT 0,
  exclusive     TINYINT(1) NOT NULL DEFAULT '0',
  created_by    VARCHAR(50) NOT NULL,
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by    VARCHAR(50) NOT NULL,
  updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uniq_env_lock (environment_id),
  FOREIGN KEY (environment_id) REFERENCES environment (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS environment_service_lock
(
  id                     BIGINT NOT NULL AUTO_INCREMENT,
  environment_id                 BIGINT NOT NULL,
  environment_service_id BIGINT NOT NULL,
  shared_count           INT NOT NULL DEFAULT 0,
  exclusive              TINYINT(1) NOT NULL DEFAULT '0',
  created_by             VARCHAR(50) NOT NULL,
  created_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by             VARCHAR(50) NOT NULL,
  updated_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uniq_env_service_lock (environment_id, environment_service_id),
  FOREIGN KEY (environment_id) REFERENCES environment (id),
  FOREIGN KEY (environment_service_id) REFERENCES environment_service (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS environment_service_component_lock
(
  id                               BIGINT NOT NULL AUTO_INCREMENT,
  env_id                           BIGINT NOT NULL,
  environment_service_id           BIGINT NOT NULL,
  environment_service_component_id BIGINT NOT NULL,
  shared_count                     INT NOT NULL DEFAULT 0,
  exclusive                        TINYINT(1) NOT NULL DEFAULT '0',
  created_by                       VARCHAR(50) NOT NULL,
  created_at                       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by                       VARCHAR(50) NOT NULL,
  updated_at                       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uniq_env_service_component_lock (env_id, environment_service_id, environment_service_component_id),
  FOREIGN KEY (env_id) REFERENCES environment (id),
  FOREIGN KEY (environment_service_id) REFERENCES environment_service (id),
  FOREIGN KEY (environment_service_component_id) REFERENCES environment_service_component (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS execution_tasks
(
  id          BIGINT NOT NULL AUTO_INCREMENT,
  action      VARCHAR(50) NOT NULL,
  org_id      BIGINT NOT NULL,
  response    JSON NOT NULL DEFAULT (JSON_OBJECT()),
  status      VARCHAR(20) NOT NULL,
  entity      VARCHAR(20) NOT NULL,
  execution_id    VARCHAR(255) DEFAULT '' NOT NULL,
  payload     JSON NOT NULL DEFAULT (JSON_OBJECT()),
  created_by  VARCHAR(50) NOT NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by  VARCHAR(50) NOT NULL,
  updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
