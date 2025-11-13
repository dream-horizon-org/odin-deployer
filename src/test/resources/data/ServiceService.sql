INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (1, '1', '1', 1, 1, 'env1', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (2, '1', '1', 1, 1, 'env2', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (3, '1', '1', 1, 1, 'env3', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (4, '1', '1', 1, 1, 'odin-operate-service-doesnt-exist', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (5, '1', '1', 1, 1, 'odin-operate-too-many-components', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (6, '1', '1', 1, 1, 'odin-operate-component-doesnt-exist', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (7, '1', '1', 1, 1, 'odin-operate-add-component', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (8, '1', '1', 1, 1, 'odin-operate-remove-component', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (9, '1', '1', 1, 1, 'odin-operate-component', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (10, '1', '1', 1, 1, 'odin-undeploy-service', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (11, '1', '1', 1, 1, 'odin-operate-service-prev-deploy-failed', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (12, '1', '1', 1, 1, 'odin-operate-component-prev-deploy-failed', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (13, '1', '1', 1, 1, 'odin-operate-service-prev-undeploy-success', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (14, '1', '1', 1, 1, 'odin-operate-component-prev-undeploy-successful', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (15, '1', '1', 1, 1, 'config-manager-plugin-env', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (16, '1', '1', 1, 1, 'account-placeholders-plugin-env', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (17, '1', '1', 1, 1, 'odin-placeholders-plugin-env', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (18, '1', '1', 1, 1, 'granulate-plugin-env', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (19, '1', '1', 1, 1, 'discovery-manager-plugin-env', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (20, '1', '1', 1, 1, 'logger-plugin-env', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (21, '1', '1', 1, 1, 'envInvalidProvisioningType', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (22, '1', '1', 1, 1, 'prod', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (23, '1', '1', 1, 1, 'auth-nv', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (24, '1', '1', 1, 1, 'custom-application-plugin-env', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (25, '1', '1', 1, 1, 'elas-agent-env', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (26, '1', '1', 1, 1, 'uat', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (27, '1', '1', 1, 1, 'serverless-plugin-env', 1);


INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (101, '1', '1', 1, 1, 5, 'SUCCESSFUL', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}', 'staging');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (102, '1', '1', 2, 1, 5, 'IN_PROGRESS', '{}', 'staging');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (103, '1', '1', 3, 1, 5, 'FAILED', '{}', 'staging');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (104, '1', '1', 4, 1, 5, 'SUCCESSFUL', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}', 'staging');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (105, '1', '1', 5, 1, 5, 'SUCCESSFUL', '{}', 'staging');


INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (106, '1', '1', 6, 1, 5, 'SUCCESSFUL', '{}', 'staging');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (107, '1', '1', 7, 1, 5, 'SUCCESSFUL', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}', 'staging');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (108, '1', '1', 8, 1, 5, 'SUCCESSFUL', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}', 'staging');


INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (109, '1', '1', 9, 1, 5, 'SUCCESSFUL', '{}', 'staging');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (110, '1', '1', 10, 1, 5, 'SUCCESSFUL', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}', 'staging');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (111, '1', '1', 11, 1, 5, 'SUCCESSFUL', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}', 'staging');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (112, '1', '1', 12, 1, 5, 'SUCCESSFUL', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}', 'staging');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (113, '1', '1', 13, 1, 5, 'SUCCESSFUL', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}', 'staging');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (114, '1', '1', 14, 1, 5, 'SUCCESSFUL', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}', 'staging');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (115, '1', '1', 15, 1, 5, 'SUCCESSFUL', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}', 'staging');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (116, '1', '1', 16, 1, 5, 'SUCCESSFUL', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}', 'staging');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (117, '1', '1', 17, 1, 5, 'SUCCESSFUL', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}', 'staging');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (118, '1', '1', 18, 1, 5, 'SUCCESSFUL', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}', 'staging');


INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (119, '1', '1', 19, 1, 5, 'SUCCESSFUL', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}', 'staging');


INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (120, '1', '1', 20, 1, 5, 'SUCCESSFUL', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}' ,'staging');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (121, '1', '1', 21, 1, 5, 'SUCCESSFUL', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}' ,'staging');


INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (123, '1', '1', 22, 1, 5, 'SUCCESSFUL', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}', 'staging');


INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (122, '1', '1', 23, 1, 5, 'SUCCESSFUL', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}' ,'staging');


INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (124, '1', '1', 24, 1, 5, 'SUCCESSFUL', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}', 'staging');


INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (125, '1', '1', 25, 1, 5, 'SUCCESSFUL', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}', 'staging');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (126, '1', '1', 26, 1, 5, 'SUCCESSFUL', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}', 'staging');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (127, '1', '1', 27, 1, 5, 'SUCCESSFUL', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}', 'staging');



INSERT INTO service_task(id, created_by, updated_by, version, name, service_version, env_id, status, action_id, config, service_config_hash)
VALUES (1, '1', '1', 1, 'odin-operate-too-many-components', '1.0.0', 5, 'SUCCESSFUL',1, '{
    "name": "odin-operate-too-many-components",
    "version": "1.0.0"
}', 'hash1' );

INSERT INTO service_task(id, created_by, updated_by, version, name, service_version, env_id, status, action_id, config, service_config_hash)
VALUES (2, '1', '1', 1, 'odin-operate-component-doesnt-exist', '1.0.0', 6, 'SUCCESSFUL',1, '{
    "name": "odin-operate-component-doesnt-exist",
    "version": "1.0.0"
}', 'hash1' );

INSERT INTO service_task(id, created_by, updated_by, version, name, service_version, env_id, status, action_id, config, service_config_hash)
VALUES (3, '1', '1', 1, 'odin-operate-add-component', '1.0.0', 1, 'SUCCESSFUL',1, '{
    "name": "odin-operate-add-component",
    "version": "1.0.0"
}', 'hash1' );

INSERT INTO service_task(id, created_by, updated_by, version, name, service_version, env_id, status, action_id, config, service_config_hash)
VALUES (4, '1', '1', 1, 'odin-operate-remove-component', '1.0.0', 8, 'SUCCESSFUL',1, '{
    "name": "odin-operate-remove-component",
    "version": "1.0.0"
}', 'hash1' );

INSERT INTO service_task(id, created_by, updated_by, version, name, service_version, env_id, status, action_id, config, service_config_hash)
VALUES (5, '1', '1', 1, 'odin-operate-component', '1.0.0', 9, 'SUCCESSFUL',1, '{
    "name": "odin-operate-component",
    "version": "1.0.0"
}', 'hash1' );

INSERT INTO service_task(id, created_by, updated_by, version, name, service_version, env_id, status, action_id, config, service_config_hash)
VALUES (6, '1', '1', 1, 'odin-undeploy-service', '1.0.0', 10, 'SUCCESSFUL',1, '{
    "name": "odin-undeploy-service",
    "version": "1.0.0"
}', 'hash1' );

INSERT INTO service_task(id, created_by, updated_by, version, name, service_version, env_id, status, action_id, config, service_config_hash)
VALUES (7, '1', '1', 1, 'odin-operate-service-prev-deploy-failed', '1.0.0', 11, 'FAILED',1, '{
    "name": "odin-operate-service-prev-deploy-failed",
    "version": "1.0.0"
}', 'hash1' );

INSERT INTO service_task(id, created_by, updated_by, version, name, service_version, env_id, status, action_id, config, service_config_hash)
VALUES (8, '1', '1', 1, 'odin-operate-component-prev-deploy-failed', '1.0.0', 12, 'FAILED',1, '{
    "name": "odin-operate-component-prev-deploy-failed",
    "version": "1.0.0"
}', 'hash1' );

INSERT INTO service_task(id, created_by, updated_by, version, name, service_version, env_id, status, action_id, config, service_config_hash)
VALUES (9, '1', '1', 1, 'odin-operate-service-prev-undeploy-success', '1.0.0', 13, 'SUCCESSFUL',2, '{
    "name": "odin-operate-service-prev-undeploy-success",
    "version": "1.0.0"
}', 'hash1' );

INSERT INTO service_task(id, created_by, updated_by, version, name, service_version, env_id, status, action_id, config, service_config_hash)
VALUES (10, '1', '1', 1, 'odin-operate-component-prev-undeploy-successful', '1.0.0', 14, 'SUCCESSFUL',2, '{
    "name": "odin-operate-component-prev-undeploy-successful",
    "version": "1.0.0"
}', 'hash1' );


INSERT INTO service_task(id, created_by, updated_by, version, name, service_version, env_id, status, action_id, config, service_config_hash)
VALUES (11, '1', '1', 1, 'odin-operate-remove-previously-removed-component', '1.0.0', 8, 'SUCCESSFUL',2, '{
    "name": "odin-operate-remove-previously-removed-component",
    "version": "1.0.0"
}', 'hash1' );


INSERT INTO service_task(id, created_by, updated_by, version, name, service_version, env_id, status, action_id, config, service_config_hash)
VALUES (12, '1', '1', 1, 'odin-operate-add-previously-removed-component', '1.0.0', 8, 'SUCCESSFUL',3, '{
    "name": "odin-operate-add-previously-removed-component",
    "version": "1.0.0"
}', 'hash1' );

INSERT INTO service_task(id, created_by, updated_by, version, name, service_version, env_id, status, action_id, config, service_config_hash)
VALUES (13, '1', '1', 1, 'odin-operate-remove-component-when-no-component-present', '1.0.0', 5, 'SUCCESSFUL',1, '{
    "name": "odin-operate-remove-component-when-no-component-present",
    "version": "1.0.0"
}', 'hash1' );

INSERT INTO service_task(id, created_by, updated_by, version, name, service_version, env_id, status, action_id, config, service_config_hash)
VALUES (14, '1', '1', 1, 'custom-application-plugin-component', '1.0.0', 24, 'SUCCESSFUL',1, '{
    "name": "odin-operate-remove-component",
    "version": "1.0.0"
}', 'hash1' );


INSERT INTO service_task(id, created_by, updated_by, version, name, service_version, env_id, status, action_id, config, service_config_hash)
VALUES (15, '1', '1', 1, 'serverless-plugin-component', '1.0.0', 27, 'SUCCESSFUL',1, '{
    "name": "test-service",
    "version": "1.0.0"
}', 'hash1' );


INSERT INTO component_task(id, version, component_name, service_task_id, config,status,created_by, updated_by, action_id, config_hash, service_account_snapshot)
VALUES (1, 1, "component1",  1, '{
  "componentConfig": {
    "type": "application",
    "name": "component1",
    "version": "1.0.0",
    "config": {
      "artifact": {
        "name": "odindemo",
        "version": "1.1.1"
      }
    }
  },
  "provisioningConfig": {
    "component_name": "component1",
    "deployment_type": "aws_ec2"
  }
}', 'SUCCESSFUL', '1', '1', 1,'hash1', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}');


INSERT INTO component_task(id, version, component_name, service_task_id, config,status,created_by, updated_by, action_id, config_hash, service_account_snapshot)
VALUES (2, 1, "component2",  1, '{
    "componentConfig": {
        "type": "application",
        "name": "component2",
        "version": "1.0.0",
        "config": {
            "artifact": {
            "name": "odindemo",
            "version": "1.1.1"
          }
        }
    },
    "provisioningConfig": {
        "component_name": "component2",
        "deployment_type": "aws_ec2"
    }
}', 'SUCCESSFUL', '1', '1', 1,'hash1', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}');

INSERT INTO component_task(id, version, component_name, service_task_id, config,status,created_by, updated_by, action_id, config_hash, service_account_snapshot)
VALUES (3, 1, "component3",  1, '{
    "componentConfig": {
        "type": "application",
        "name": "component3",
        "version": "1.0.0",
        "config": {
            "artifact": {
              "name": "odindemo",
              "version": "1.1.1"
            }
        }
    },
    "provisioningConfig": {
        "component_name": "component3",
        "deployment_type": "aws_ec2"
    }
}', 'SUCCESSFUL', '1', '1', 1,'hash1', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}');

INSERT INTO component_task(id, version, component_name, service_task_id, config, status, created_by, updated_by, action_id, config_hash, service_account_snapshot)
VALUES (5, 1, "cj-frontend-v1", 6, '{
    "componentConfig": {
        "name": "cj-frontend-v1",
        "type": "application",
        "config": {
            "artifact": {
              "name": "contest-join",
              "version": "1.0.0"
            }
        }
    },
    "provisioningConfig": {
        "component_name": "cj-frontend-v1",
        "deployment_type": "aws_ec2",
        "params": {
            "num_instances": 2,
            "lb_type": "elb"
        }
    }
}', 'SUCCESSFUL', '196', '196',1,'hash1', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}');

INSERT INTO component_task(id, version, component_name, service_task_id, config, status, created_by, updated_by, action_id, config_hash, service_account_snapshot)
VALUES (6, 1, "odindemo5", 6, '{
    "componentConfig": {
        "type": "application",
        "name": "odindemo5",
        "version": "1.0.0",
        "config": {
            "artifact": {
              "name": "odindemo",
              "version": "1.1.1"
            }
        }
    },
    "provisioningConfig": {
        "component_name": "odindemo5",
        "deployment_type": "aws_ec2"
    }
}', 'SUCCESSFUL', '196', '196',1,'hash1', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}');

INSERT INTO component_task(id, version, component_name, service_task_id, config, status, created_by, updated_by, action_id, config_hash, service_account_snapshot)
VALUES (7, 1, "odindemo5", 4, '{
    "componentConfig": {
        "type": "application",
        "name": "odindemo5",
        "version": "1.0.0",
        "config": {
            "artifact": {
              "name": "odindemo",
              "version": "1.1.1"
            }
        }
    },
    "provisioningConfig": {
        "component_name": "odindemo5",
        "deployment_type": "aws_ec2"
    }
}', 'SUCCESSFUL', '196', '196',1,'hash1', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}');

INSERT INTO component_task(id, version, component_name, service_task_id, config, status, created_by, updated_by, action_id, config_hash, service_account_snapshot)
VALUES (9, 1, "cj-frontend-v1", 5, '{
    "componentConfig":{
        "name": "cj-frontend-v1",
        "type": "application",
        "config": {
            "artifact": {
              "name": "contest-join",
              "version": "1.0.0"
            }
        }
    },
    "provisioningConfig":{
        "component_name": "cj-frontend-v1",
        "deployment_type": "aws_ec2",
        "params": {
            "num_instances": 2,
            "lb_type": "elb"
        }
    }
}', 'SUCCESSFUL', '196', '196',1,'hash1', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}');

INSERT INTO component_task(id, version, component_name, service_task_id, config, status, created_by, updated_by, action_id, config_hash, service_account_snapshot)
VALUES (10, 1, "odindemo5", 5, '{
    "componentConfig": {
        "type": "application",
        "name": "odindemo5",
        "version": "1.0.0",
        "config": {
            "artifact": {
              "name": "odindemo",
              "version": "1.1.1"
            }
        }
    },
    "provisioningConfig": {
        "component_name": "odindemo5",
        "deployment_type": "aws_ec2"
    }
}', 'SUCCESSFUL', '196', '196',1,'hash1', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}');


INSERT INTO component_task(id, version, component_name, service_task_id, config,status,created_by, updated_by, action_id, config_hash, service_account_snapshot)
VALUES (11, 1, "component1",  2, '{
    "componentConfig": {
        "type": "application",
        "name": "component1",
        "version": "1.0.0",
        "config": {
            "artifact": {
              "name": "odindemo",
              "version": "1.1.1"
            }
        }
    },
    "provisioningConfig": {
        "component_name": "component1",
        "deployment_type": "aws_ec2"
    }
}', 'SUCCESSFUL', '1', '1', 2,'hash1', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}');

INSERT INTO component_task(id, version, component_name, service_task_id, config,status,created_by, updated_by, action_id, config_hash, service_account_snapshot)
VALUES (12, 1, "cj-frontend-v1",  4, '{
    "componentConfig":{
        "name": "cj-frontend-v1",
        "type": "application",
        "config": {
            "artifact": {
              "name": "contest-join",
              "version": "1.0.0"
            }
        }
    },
    "provisioningConfig":{
        "component_name": "cj-frontend-v1",
        "deployment_type": "aws_ec2",
        "params": {
            "num_instances": 2,
            "lb_type": "elb"
        }
    }
}', 'SUCCESSFUL', '1', '1', 1,'hash1', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}');

INSERT INTO component_task(id, version, component_name, service_task_id, config,status,created_by, updated_by, action_id, config_hash, service_account_snapshot)
VALUES (14, 1, "cj-frontend-v1",  11, '{
    "componentConfig": {
        "name": "cj-frontend-v1",
        "type": "application",
        "config": {
            "artifact": {
              "name": "contest-join",
              "version": "1.0.0"
            }
        }
    },
    "provisioningConfig": {
        "component_name": "cj-frontend-v1",
        "deployment_type": "aws_ec2",
        "params": {
            "num_instances": 2,
            "lb_type": "elb"
        }
    }
}', 'SUCCESSFUL', '1', '1', 2,'hash1', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}');

INSERT INTO component_task(id, version, component_name, service_task_id, config,status,created_by, updated_by, action_id, config_hash, service_account_snapshot)
VALUES (15, 1, "odindemo5",  11, '{
    "componentConfig": {
        "type": "application",
        "name": "odindemo5",
        "version": "1.0.0",
        "config": {
            "artifact": {
              "name": "odindemo",
              "version": "1.1.1"
            }
        }
    },
    "provisioningConfig": {
        "component_name": "odindemo5",
        "deployment_type": "aws_ec2"
    }
}', 'SUCCESSFUL', '1', '1', 1,'hash1', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}');



INSERT INTO component_task(id, version, component_name, service_task_id, config, status, created_by, updated_by, action_id, config_hash, service_account_snapshot)
VALUES (8, 1, "cj-frontend-v1", 4, '{"scale":1}', 'SUCCESSFUL', '196', '196',1,'hash1','{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}');

INSERT INTO component_task(id, version, component_name, service_task_id, config,status,created_by, updated_by, action_id, config_hash, service_account_snapshot)
VALUES (16, 1, "cj-frontend-v1",  3, '{
    "componentConfig": {
        "name": "cj-frontend-v1",
        "type": "application",
        "config": {
            "artifact": {
              "name": "contest-join",
              "version": "1.0.0"
            }
        }
    },
    "provisioningConfig": {
        "component_name": "cj-frontend-v1",
        "deployment_type": "aws_ec2",
        "params": {
            "num_instances": 2,
            "lb_type": "elb"
        }
    }
}', 'SUCCESSFUL', '1', '1', 1,'hash1', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}');



INSERT INTO component_task(id, version, component_name, service_task_id, config,status,created_by, updated_by, action_id, config_hash, service_account_snapshot)
VALUES (17, 1, "cj-frontend-v1",  12, '{
    "componentConfig": {
        "name": "cj-frontend-v1",
        "type": "application",
        "config": {
            "artifact": {
              "name": "contest-join",
              "version": "1.0.0"
            }
        }
    },
    "provisioningConfig": {
        "component_name": "cj-frontend-v1",
        "deployment_type": "aws_ec2",
        "params": {
            "num_instances": 2,
            "lb_type": "elb"
        }
    }
}', 'SUCCESSFUL', '1', '1', 2,'hash1', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}');

INSERT INTO component_task(id, version, component_name, service_task_id, config,status,created_by, updated_by, action_id, config_hash, service_account_snapshot)
VALUES (18, 1, "odindemo5",  12, '{
    "componentConfig": {
        "type": "application",
        "name": "odindemo5",
        "version": "1.0.0",
        "config": {
            "artifact": {
              "name": "odindemo",
              "version": "1.1.1"
            }
        }
    },
    "provisioningConfig": {
        "component_name": "odindemo5",
        "deployment_type": "aws_ec2"
    }
}', 'SUCCESSFUL', '1', '1', 1,'hash1', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}');

INSERT INTO component_task(id, version, component_name, service_task_id, config, status, created_by, updated_by, action_id, config_hash, service_account_snapshot)
VALUES (19, 1, "odindemo5", 8, '{
    "componentConfig": {
        "type": "application",
        "name": "odindemo5",
        "version": "1.0.0",
        "config": {
            "build_type": "java",
            "build_version": "11",
            "artifact_name": "odindemo",
            "artifact_version": "1.1.1"
        }
    },
    "provisioningConfig": {
        "component_name": "odindemo5",
        "deployment_type": "aws_ec2"
    }
}', 'SUCCESSFUL', '196', '196',1,'hash1', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}');

INSERT INTO component_task(id, version, component_name, service_task_id, config, status, created_by, updated_by, action_id, config_hash, service_account_snapshot)
VALUES (20, 1, "custom-application-plugin-component", 14, '{
    "componentConfig": {
        "type": "application",
        "name": "custom-application-plugin-component",
        "version": "1.0.0",
        "config": {
            "artifact": {
              "name": "odindemo",
              "version": "1.1.1"
            }
        }
    },
    "provisioningConfig": {
        "component_name": "custom-application-plugin-component",
        "deployment_type": "aws_ec2"
    }

}', 'SUCCESSFUL', '196', '196',1,'hash1', '{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}');
INSERT INTO component_task(id, version, component_name, service_task_id, config, status, created_by, updated_by, action_id, config_hash, service_account_snapshot)
VALUES (21, 1, "odindemo5", 10, '{"scale":1}', 'SUCCESSFUL', '196', '196',2,'hash1','{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}');
