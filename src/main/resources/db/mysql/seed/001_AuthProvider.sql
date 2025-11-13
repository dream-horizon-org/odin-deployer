--liquibase formatted sql

--changeset odin:001_auth_provider context:seed
INSERT INTO auth_provider (org_id, type, provider_details)
VALUES (0, 'ANONYMOUS', '{}')
ON DUPLICATE KEY UPDATE type = VALUES(type), provider_details = VALUES(provider_details);

INSERT INTO auth_provider (org_id, `type`, provider_details)
VALUES (
  1,
  'OIDC',
  JSON_OBJECT(
    'name', 'Google',
    'client_id', '393833795300-3sm8kt0bjjjrsv27gi0pjpia2m9gg2i1.apps.googleusercontent.com',
    'client_secret', 'TEST_CLIENT_SECRET_DUMMY_VALUE',
    'authorization_url', 'https://accounts.google.com/o/oauth2/auth',
    'token_url',        'https://oauth2.googleapis.com/token',
    'scope',            'email'
  )
)
AS new
ON DUPLICATE KEY UPDATE
  `type` = new.`type`,
  provider_details = new.provider_details;
