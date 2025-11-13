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
