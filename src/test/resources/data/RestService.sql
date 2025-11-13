INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (9, '1', '1', 1, 1, 'odin-operate-component', 1);


INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (109, '1', '1', 9, 1, 5, 'SUCCESSFUL', '{}', 'staging');


INSERT INTO service_task(id, created_by, updated_by, version, name, service_version, env_id, status, action_id, config, service_config_hash)
VALUES (5, '1', '1', 1, 'odin-operate-component', '1.0.0', 9, 'SUCCESSFUL',1, '{
    "name": "odin-operate-component",
    "version": "1.0.0"
}', 'hash1' );


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
