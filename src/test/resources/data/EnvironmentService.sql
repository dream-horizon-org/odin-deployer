INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (1, '1', '1', 1, 1, 'env1', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (2, '196', '196', 1, 1, 'env196', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (3, '196', '196', 1, 1, 'env-far-deletion', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (4, '196', '196', 0, 1, 'env-near-deletion', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (5, '196', '196', 1, 1, 'response-test', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (6, '1', '1', 1, 1, 'env-for-delete', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (7, '1', '1', 1, 1, 'env-delete-failure', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (8, '1', '1', 1, 1, 'env-delete-partial-failure', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (9, '1', '1', 1, 1, 'env-delete-no-service', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (10, '1', '1', 1, 1, 'env-delete-services-status-invalid', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (11, '196', '196', 0, 1, 'env-near-deletion-failed', 1);

INSERT INTO environment(id, created_by, updated_by, version, org_id, name, is_active)
VALUES (12, 'anonymous', 'anonymous', 1, 1, 'env-anonymous-user', 1);


INSERT INTO service_task(id, created_by, updated_by, version, name, service_version, env_id, status, action_id, config, service_config_hash)
VALUES (1, '196', '196', 1, 'service1', '1.0.0', 2, 'SUCCESSFUL',1, '{"key": "value1"}', 'hash1' );

INSERT INTO service_task(id, created_by, updated_by, version, name, service_version, env_id, status, action_id, config, service_config_hash)
VALUES (2, '196', '196', 1, 'service2', '1.0.0', 1, 'SUCCESSFUL', 1, '{"key": "value2"}', 'hash2' );

INSERT INTO service_task(id, created_by, updated_by, version, name, service_version, env_id, status, action_id, config, service_config_hash)
VALUES (21, '196', '196', 2, 'service1', '1.0.0', 2, 'SUCCESSFUL',2, '{"key": "value1"}', 'hash1' );

INSERT INTO service_task(id, created_by, updated_by, version, name, service_version, env_id, status, action_id, config, service_config_hash)
VALUES (22, '196', '196', 3, 'service1', '1.0.0', 2, 'SUCCESSFUL',1, '{"key": "value1"}', 'hash1' );

INSERT INTO service_task(id, created_by, updated_by, version, name, service_version, env_id, status, action_id, config, service_config_hash)
VALUES (23, '196', '196', 4, 'service1', '1.0.0', 2, 'SUCCESSFUL',3, '{"key": "value1"}', 'hash1' );



INSERT INTO component_task(id, version, component_name, service_task_id, config, status, created_by, updated_by, action_id, config_hash)
VALUES (1, 1, "component1s1", 1, '{"componentConfig": {"name": "component1s1", "type": "application", "config": { "testCKey":"c1",
"artifact": {"name": "odindemo", "hooks": {"stop": {"script": ".odin/stop.sh", "enabled": true}, "start": {"script": ".odin/start.sh", "enabled": true}, "preDeploy": {"script": ".odin/pre-deploy.sh", "enabled": false, "dockerImage": "docker.io/example/executor:0.0.1"}, "imageSetup": {"script": ".odin/setup.sh", "enabled": true}, "postDeploy": {"script": ".odin/post-deploy.sh", "enabled": false, "dockerImage": "docker.io/example/executor:0.0.1"}}, "version": "1.1.7"}, "discovery": {"type": "private", "public": "test-odindemo-nik-d3-stg1.d11dev.com", "private": "test-odindemo-nik-d3-stg1.dream11-stag.local"}}, "version": "0.0.5"}, "provisioningConfig": {  "params": { "testPKey":"p1","asg": {"instancePool": ["t2.medium", "c5.2xlarge", "c5.xlarge", "t3.medium"], "maxInstances": 4.0, "initialCapacity": 2.0, "desiredInstances": 4.0, "onDemandBaseCapacity": 1.0}, "logs": {"enabled": false}, "tags": {"service_name": "odin", "resource_type": "aws_ec2", "component_name": "odin", "component_type": "application", "environment_name": "stag", "provisioned-by-user": "test@dream11.com"}, "stacks": 1.0, "strategy": {"name": "blue-green", "config": {"canary": {"enabled": false}, "autoRouting": true, "passiveDownscale": {"enabled": false}}}, "baseImage": {"filters": {"name": "odin-golden-ami-debian-java-11-*", "root-device-type": "ebs", "virtualization-type": "hvm"}, "sshUser": "admin", "instanceType": "c5.2xlarge"}, "extraEnvVars": {"ENV": "dev", "NAMESPACE": "master", "VPC_SUFFIX": "-stag", "TEAM_SUFFIX": "-stg1-n-9", "SERVICE_NAME": "odin-account-manager"}, "loadBalancer": {"type": "clb", "listeners": [{"port": 80.0, "protocol": "HTTP", "targetPort": 8080.0, "targetProtocol": "HTTP"}]}}, "componentName": "d3", "deploymentType": "aws_ec2"}}'
       , 'SUCCESSFUL', '196', '196',1,'hash1');

INSERT INTO component_task(id, version, component_name, service_task_id, config, status, created_by, updated_by, action_id, config_hash)
VALUES (21, 1, "component1s1", 21, '{"componentConfig": {"name": "component1s1", "type": "application", "config": {"testCKey":"c2",
"artifact": {"name": "odindemo", "hooks": {"stop": {"script": ".odin/stop.sh", "enabled": true}, "start": {"script": ".odin/start.sh", "enabled": true}, "preDeploy": {"script": ".odin/pre-deploy.sh", "enabled": false, "dockerImage": "docker.io/example/executor:0.0.1"}, "imageSetup": {"script": ".odin/setup.sh", "enabled": true}, "postDeploy": {"script": ".odin/post-deploy.sh", "enabled": false, "dockerImage": "docker.io/example/executor:0.0.1"}}, "version": "1.1.7"}, "discovery": {"type": "private", "public": "test-odindemo-nik-d3-stg1.d11dev.com", "private": "test-odindemo-nik-d3-stg1.dream11-stag.local"}}, "version": "0.0.5"}, "provisioningConfig": { "params": {"testPKey":"p2","asg": {"instancePool": ["t2.medium", "c5.2xlarge", "c5.xlarge", "t3.medium"], "maxInstances": 4.0, "initialCapacity": 2.0, "desiredInstances": 4.0, "onDemandBaseCapacity": 1.0}, "logs": {"enabled": false}, "tags": {"service_name": "odin", "resource_type": "aws_ec2", "component_name": "odin", "component_type": "application", "environment_name": "stag", "provisioned-by-user": "test@dream11.com"}, "stacks": 1.0, "strategy": {"name": "blue-green", "config": {"canary": {"enabled": false}, "autoRouting": true, "passiveDownscale": {"enabled": false}}}, "baseImage": {"filters": {"name": "odin-golden-ami-debian-java-11-*", "root-device-type": "ebs", "virtualization-type": "hvm"}, "sshUser": "admin", "instanceType": "c5.2xlarge"}, "extraEnvVars": {"ENV": "dev", "NAMESPACE": "master", "VPC_SUFFIX": "-stag", "TEAM_SUFFIX": "-stg1-n-9", "SERVICE_NAME": "odin-account-manager"}, "loadBalancer": {"type": "clb", "listeners": [{"port": 80.0, "protocol": "HTTP", "targetPort": 8080.0, "targetProtocol": "HTTP"}]}}, "componentName": "d3", "deploymentType": "aws_ec2"}}'
       ,'SUCCESSFUL', '196', '196',2,'hash1');

INSERT INTO component_task(id, version, component_name, service_task_id, config, status, created_by, updated_by, action_id, config_hash)
VALUES (22, 1, "component2s1", 21, '{"componentConfig": {"name": "component2s1", "type": "application", "config": {"testCKey":"c2",
"artifact": {"name": "odindemo", "hooks": {"stop": {"script": ".odin/stop.sh", "enabled": true}, "start": {"script": ".odin/start.sh", "enabled": true}, "preDeploy": {"script": ".odin/pre-deploy.sh", "enabled": false, "dockerImage": "docker.io/example/executor:0.0.1"}, "imageSetup": {"script": ".odin/setup.sh", "enabled": true}, "postDeploy": {"script": ".odin/post-deploy.sh", "enabled": false, "dockerImage": "docker.io/example/executor:0.0.1"}}, "version": "1.1.7"}, "discovery": {"type": "private", "public": "test-odindemo-nik-d3-stg1.d11dev.com", "private": "test-odindemo-nik-d3-stg1.dream11-stag.local"}}, "version": "0.0.5"}, "provisioningConfig": { "params": {"testPKey":"p2","asg": {"instancePool": ["t2.medium", "c5.2xlarge", "c5.xlarge", "t3.medium"], "maxInstances": 4.0, "initialCapacity": 2.0, "desiredInstances": 4.0, "onDemandBaseCapacity": 1.0}, "logs": {"enabled": false}, "tags": {"service_name": "odin", "resource_type": "aws_ec2", "component_name": "odin", "component_type": "application", "environment_name": "stag", "provisioned-by-user": "test@dream11.com"}, "stacks": 1.0, "strategy": {"name": "blue-green", "config": {"canary": {"enabled": false}, "autoRouting": true, "passiveDownscale": {"enabled": false}}}, "baseImage": {"filters": {"name": "odin-golden-ami-debian-java-11-*", "root-device-type": "ebs", "virtualization-type": "hvm"}, "sshUser": "admin", "instanceType": "c5.2xlarge"}, "extraEnvVars": {"ENV": "dev", "NAMESPACE": "master", "VPC_SUFFIX": "-stag", "TEAM_SUFFIX": "-stg1-n-9", "SERVICE_NAME": "odin-account-manager"}, "loadBalancer": {"type": "clb", "listeners": [{"port": 80.0, "protocol": "HTTP", "targetPort": 8080.0, "targetProtocol": "HTTP"}]}}, "componentName": "d3", "deploymentType": "aws_ec2"}}'
       ,'SUCCESSFUL', '196', '196',2,'hash1');


INSERT INTO component_task(id, version, component_name, service_task_id, config, status, created_by, updated_by, action_id, config_hash)
VALUES (31, 1, "component1s1", 22, '{"componentConfig": {"name": "component1s1", "type": "application", "config": {"testCKey":"c3",
"artifact": {"name": "odindemo", "hooks": {"stop": {"script": ".odin/stop.sh", "enabled": true}, "start": {"script": ".odin/start.sh", "enabled": true}, "preDeploy": {"script": ".odin/pre-deploy.sh", "enabled": false, "dockerImage": "docker.io/example/executor:0.0.1"}, "imageSetup": {"script": ".odin/setup.sh", "enabled": true}, "postDeploy": {"script": ".odin/post-deploy.sh", "enabled": false, "dockerImage": "docker.io/example/executor:0.0.1"}}, "version": "1.1.7"}, "discovery": {"type": "private", "public": "test-odindemo-nik-d3-stg1.d11dev.com", "private": "test-odindemo-nik-d3-stg1.dream11-stag.local"}}, "version": "0.0.5"}, "provisioningConfig": { "params": {"testPKey":"p3","asg": {"instancePool": ["t2.medium", "c5.2xlarge", "c5.xlarge", "t3.medium"], "maxInstances": 4.0, "initialCapacity": 2.0, "desiredInstances": 4.0, "onDemandBaseCapacity": 1.0}, "logs": {"enabled": false}, "tags": {"service_name": "odin", "resource_type": "aws_ec2", "component_name": "odin", "component_type": "application", "environment_name": "stag", "provisioned-by-user": "test@dream11.com"}, "stacks": 1.0, "strategy": {"name": "blue-green", "config": {"canary": {"enabled": false}, "autoRouting": true, "passiveDownscale": {"enabled": false}}}, "baseImage": {"filters": {"name": "odin-golden-ami-debian-java-11-*", "root-device-type": "ebs", "virtualization-type": "hvm"}, "sshUser": "admin", "instanceType": "c5.2xlarge"}, "extraEnvVars": {"ENV": "dev", "NAMESPACE": "master", "VPC_SUFFIX": "-stag", "TEAM_SUFFIX": "-stg1-n-9", "SERVICE_NAME": "odin-account-manager"}, "loadBalancer": {"type": "clb", "listeners": [{"port": 80.0, "protocol": "HTTP", "targetPort": 8080.0, "targetProtocol": "HTTP"}]}}, "componentName": "d3", "deploymentType": "aws_ec2"}}'
       , 'SUCCESSFUL', '196', '196',1,'hash1');

INSERT INTO component_task(id, version, component_name, service_task_id, config, status, created_by, updated_by, action_id, config_hash)
VALUES (32, 1, "component2s1", 22, '{"componentConfig": {"name": "component2s1", "type": "application", "config": {"testCKey":"c3",
"artifact": {"name": "odindemo", "hooks": {"stop": {"script": ".odin/stop.sh", "enabled": true}, "start": {"script": ".odin/start.sh", "enabled": true}, "preDeploy": {"script": ".odin/pre-deploy.sh", "enabled": false, "dockerImage": "docker.io/example/executor:0.0.1"}, "imageSetup": {"script": ".odin/setup.sh", "enabled": true}, "postDeploy": {"script": ".odin/post-deploy.sh", "enabled": false, "dockerImage": "docker.io/example/executor:0.0.1"}}, "version": "1.1.7"}, "discovery": {"type": "private", "public": "test-odindemo-nik-d3-stg1.d11dev.com", "private": "test-odindemo-nik-d3-stg1.dream11-stag.local"}}, "version": "0.0.5"}, "provisioningConfig": { "params": {"testPKey":"p3","asg": {"instancePool": ["t2.medium", "c5.2xlarge", "c5.xlarge", "t3.medium"], "maxInstances": 4.0, "initialCapacity": 2.0, "desiredInstances": 4.0, "onDemandBaseCapacity": 1.0}, "logs": {"enabled": false}, "tags": {"service_name": "odin", "resource_type": "aws_ec2", "component_name": "odin", "component_type": "application", "environment_name": "stag", "provisioned-by-user": "test@dream11.com"}, "stacks": 1.0, "strategy": {"name": "blue-green", "config": {"canary": {"enabled": false}, "autoRouting": true, "passiveDownscale": {"enabled": false}}}, "baseImage": {"filters": {"name": "odin-golden-ami-debian-java-11-*", "root-device-type": "ebs", "virtualization-type": "hvm"}, "sshUser": "admin", "instanceType": "c5.2xlarge"}, "extraEnvVars": {"ENV": "dev", "NAMESPACE": "master", "VPC_SUFFIX": "-stag", "TEAM_SUFFIX": "-stg1-n-9", "SERVICE_NAME": "odin-account-manager"}, "loadBalancer": {"type": "clb", "listeners": [{"port": 80.0, "protocol": "HTTP", "targetPort": 8080.0, "targetProtocol": "HTTP"}]}}, "componentName": "d3", "deploymentType": "aws_ec2"}}'
       , 'SUCCESSFUL', '196', '196',1,'hash1');

INSERT INTO component_task(id, version, component_name, service_task_id, config, status, created_by, updated_by, action_id, config_hash)
VALUES (41, 1, "component1s1", 23, '{"componentConfig":{"name":"component1s1","type":"application","config":{"testCKey":"c4",
"artifact":{"name":"odindemo","hooks":{"stop":{"script":".odin/stop.sh","enabled":true},"start":{"script":".odin/start.sh","enabled":true},"preDeploy":{"script":".odin/pre-deploy.sh","enabled":false,"dockerImage":"docker.io/example/executor:0.0.1"},"imageSetup":{"script":".odin/setup.sh","enabled":true},"postDeploy":{"script":".odin/post-deploy.sh","enabled":false,"dockerImage":"docker.io/example/executor:0.0.1"}},"version":"1.1.7"},"discovery":{"type":"private","public":"test-odindemo-nik-d3-stg1.d11dev.com","private":"test-odindemo-nik-d3-stg1.dream11-stag.local"}},"version":"0.0.5"},"provisioningConfig":{"params":{"testPKey":"p4","asg":{"instancePool":["t2.medium","c5.2xlarge","c5.xlarge","t3.medium"],"maxInstances":4,"initialCapacity":2,"desiredInstances":4,"onDemandBaseCapacity":1},"logs":{"enabled":false},"tags":{"service_name":"odin","resource_type":"aws_ec2","component_name":"odin","component_type":"application","environment_name":"stag","provisioned-by-user":"test@dream11.com"},"stacks":1,"strategy":{"name":"blue-green","config":{"canary":{"enabled":false},"autoRouting":true,"passiveDownscale":{"enabled":false}}},"baseImage":{"filters":{"name":"odin-golden-ami-debian-java-11-*","root-device-type":"ebs","virtualization-type":"hvm"},"sshUser":"admin","instanceType":"c5.2xlarge"},"extraEnvVars":{"ENV":"dev","NAMESPACE":"master","VPC_SUFFIX":"-stag","TEAM_SUFFIX":"-stg1-n-9","SERVICE_NAME":"odin-account-manager"},"loadBalancer":{"type":"clb","listeners":[{"port":80,"protocol":"HTTP","targetPort":8080,"targetProtocol":"HTTP"}]}},"componentName":"d3","deploymentType":"aws_ec2"},"operationConfig":{"artifact":{"version":"1.1.7-SNAPSHOT"}}}'
       , 'SUCCESSFUL', '196', '196',3,'hash1');

INSERT INTO component_task(id, version, component_name, service_task_id, config, status, created_by, updated_by, action_id, config_hash)
VALUES (42, 1, "component2s1", 23, '{"componentConfig":{"name":"component2s1","type":"application","config":{"testCKey":"c4",
"artifact":{"name":"odindemo","hooks":{"stop":{"script":".odin/stop.sh","enabled":true},"start":{"script":".odin/start.sh","enabled":true},"preDeploy":{"script":".odin/pre-deploy.sh","enabled":false,"dockerImage":"docker.io/example/executor:0.0.1"},"imageSetup":{"script":".odin/setup.sh","enabled":true},"postDeploy":{"script":".odin/post-deploy.sh","enabled":false,"dockerImage":"docker.io/example/executor:0.0.1"}},"version":"1.1.7"},"discovery":{"type":"private","public":"test-odindemo-nik-d3-stg1.d11dev.com","private":"test-odindemo-nik-d3-stg1.dream11-stag.local"}},"version":"0.0.5"},"provisioningConfig":{"params":{"testPKey":"p4","asg":{"instancePool":["t2.medium","c5.2xlarge","c5.xlarge","t3.medium"],"maxInstances":4,"initialCapacity":2,"desiredInstances":4,"onDemandBaseCapacity":1},"logs":{"enabled":false},"tags":{"service_name":"odin","resource_type":"aws_ec2","component_name":"odin","component_type":"application","environment_name":"stag","provisioned-by-user":"test@dream11.com"},"stacks":1,"strategy":{"name":"blue-green","config":{"canary":{"enabled":false},"autoRouting":true,"passiveDownscale":{"enabled":false}}},"baseImage":{"filters":{"name":"odin-golden-ami-debian-java-11-*","root-device-type":"ebs","virtualization-type":"hvm"},"sshUser":"admin","instanceType":"c5.2xlarge"},"extraEnvVars":{"ENV":"dev","NAMESPACE":"master","VPC_SUFFIX":"-stag","TEAM_SUFFIX":"-stg1-n-9","SERVICE_NAME":"odin-account-manager"},"loadBalancer":{"type":"clb","listeners":[{"port":80,"protocol":"HTTP","targetPort":8080,"targetProtocol":"HTTP"}]}},"componentName":"d3","deploymentType":"aws_ec2"},"operationConfig":{"artifact":{"version":"1.1.7-SNAPSHOT"}}}'
       , 'SUCCESSFUL', '196', '196',3,'hash1');




INSERT INTO component_task(id, version, component_name, service_task_id, config,status,created_by, updated_by, action_id, config_hash)
VALUES (2, 1, "component2s1",  1, '{"componentConfig":{"name":"c6","type":"application","config":{"artifact":{"name":"odindemo","hooks":{"stop":{"script":".odin/stop.sh","enabled":true},"start":{"script":".odin/start.sh","enabled":true},"preDeploy":{"script":".odin/pre-deploy.sh","enabled":false,"dockerImage":"docker.io/example/executor:0.0.1"},"imageSetup":{"script":".odin/setup.sh","enabled":true},"postDeploy":{"script":".odin/post-deploy.sh","enabled":false,"dockerImage":"docker.io/example/executor:0.0.1"}},"version":"1.1.7"},"discovery":{"type":"private","public":"test-odindemo-nik-c6-stg1.d11dev.com","private":"test-odindemo-nik-c6-stg1.dream11-stag.local"}},"version":"0.0.4-rah-SNAPSHOT"},"operationConfig":{"artifact":{"version":"1.1.7-SNAPSHOT"},"strategy":{"name":"blue-green","config":{"canary":{"enabled":false},"autoRouting":false,"passiveDownscale":{"enabled":true}}}},"provisioningConfig":{"params":{"asg":{"instancePool":["t2.medium","c5.2xlarge","c5.xlarge"],"initialCapacity":1,"desiredInstances":3},"logs":{"enabled":false},"tags":{"service_name":"odin","resource_type":"aws_ec2","component_name":"odin","component_type":"application","environment_name":"stag","provisioned-by-user":"test@dream11.com"},"stacks":1,"strategy":{"name":"blue-green","config":{"canary":{"enabled":false},"autoRouting":true,"passiveDownscale":{"enabled":true}}},"baseImage":{"filters":{"name":"odin-golden-ami-java-11-*","root-device-type":"ebs","virtualization-type":"hvm"},"sshUser":"centos","instanceType":"c5.2xlarge"},"extraEnvVars":{"ENV":"dev"},"loadBalancer":{"type":"alb","listeners":[{"port":80,"protocol":"HTTP","targetPort":8080,"targetProtocol":"HTTP"}]}},"componentName":"c6","deploymentType":"aws_ec2"}}'
       , 'SUCCESSFUL', '196', '196', 1,'hash1');

INSERT INTO component_task(id, version, component_name, service_task_id, config, status,created_by, updated_by, action_id, config_hash)
VALUES (3, 1, "component1s2", 2, '{"scale":3}', 'FAILED', '196', '196', 2,'hash1');


INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (100, '196', '196', 5, 1, 5, 'IN_PROGRESS', '{}', 'staging');


INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (101, '196', '196', 5, 1, 5, 'IN_PROGRESS', '{}', 'staging');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (102, '196', '196', 3, 1, 5, 'SUCCESSFUL', '{}', 'staging');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (103, '196', '196', 2, 1, 5, 'SUCCESSFUL', '{}', 'staging');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (104, '196', '196', 4, 1, 5, 'SUCCESSFUL', '{}', 'staging');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (105, '196', '196', 1, 1, 5, 'SUCCESSFUL', '{}', 'staging');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (106, '1', '1', 6, 1, 5, 'SUCCESSFUL', '{
  "account": {
    "name": "MockProvider",
    "provider": "MockProvider",
    "category": "KUBERNETES",
    "data": {
      "clusters": [
        { "name": "cluster1" }
      ]
    },
    "services": [
    {
      "name": "MockServiceAccount",
      "category": "KUBERNETES",
      "data": {
        "clusters": [
          { "name": "cluster3" },
          { "name": "cluster4" }
        ]
      },
      "id": 987654321
    }
    ],
    "default": true,
    "id": 123456789
}
}', 'MockProvider');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (107, '1', '1', 7, 1, 6, 'SUCCESSFUL', '{
  "account": {
    "name": "MockProvider",
    "provider": "MockProvider",
    "category": "KUBERNETES",
    "data": {
      "clusters": [
        { "name": "cluster1" }
      ]
    },
    "services": [
    {
      "name": "MockServiceAccount",
      "category": "KUBERNETES",
      "data": {
        "clusters": [
          { "name": "cluster3" },
          { "name": "cluster4" }
        ]
      },
      "id": 987654321
    }
    ],
    "default": true,
    "id": 123456789
}
}', 'MockProvider');


INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (108, '1', '1', 8, 1, 5, 'SUCCESSFUL', '{
  "account": {
    "name": "MockProvider",
    "provider": "MockProvider",
    "category": "KUBERNETES",
    "data": {
      "clusters": [
        { "name": "cluster1" }
      ]
    },
    "services": [
    {
      "name": "MockServiceAccount",
      "category": "KUBERNETES",
      "data": {
        "clusters": [
          { "name": "cluster3" },
          { "name": "cluster4" }
        ]
      },
      "id": 987654321
    }
    ],
    "default": true,
    "id": 123456789
}
}', 'MockProvider');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (109, '1', '1', 9, 1, 5, 'SUCCESSFUL', '{
  "account": {
    "name": "MockProvider",
    "provider": "MockProvider",
    "category": "KUBERNETES",
    "data": {
      "clusters": [
        { "name": "cluster1" }
      ]
    },
    "services": [
    {
      "name": "MockServiceAccount",
      "category": "KUBERNETES",
      "data": {
        "clusters": [
          { "name": "cluster3" },
          { "name": "cluster4" }
        ]
      },
      "id": 987654321
    }
    ],
    "default": true,
    "id": 123456789
}
}', 'MockProvider');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (110, '1', '1', 10, 1, 5, 'SUCCESSFUL', '{
  "account": {
    "name": "MockProvider",
    "provider": "MockProvider",
    "category": "KUBERNETES",
    "data": {
      "clusters": [
        { "name": "cluster1" }
      ]
    },
    "services": [
    {
      "name": "MockServiceAccount",
      "category": "KUBERNETES",
      "data": {
        "clusters": [
          { "name": "cluster3" },
          { "name": "cluster4" }
        ]
      },
      "id": 987654321
    }
    ],
    "default": true,
    "id": 123456789
}
}', 'MockProvider');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (111, '1', '1', 11, 1, 5, 'SUCCESSFUL', '{
  "account": {
    "name": "MockProvider",
    "provider": "MockProvider",
    "category": "KUBERNETES",
    "data": {
      "clusters": [
        { "name": "cluster1" }
      ]
    },
    "services": [
    {
      "name": "MockServiceAccount",
      "category": "KUBERNETES",
      "data": {
        "clusters": [
          { "name": "cluster3" },
          { "name": "cluster4" }
        ]
      },
      "id": 987654321
    }
    ],
    "default": true,
    "id": 123456789
}
}', 'MockProvider');

INSERT INTO environment_task(id, created_by, updated_by, env_id, version, action_id, status, service_accounts_snapshot,
                             provider_account_name)
VALUES (112, 'anonymous', 'anonymous', 12, 1, 5, 'SUCCESSFUL', '{}', 'staging');

INSERT INTO service_task(id, created_by, updated_by, version, name, service_version, env_id, status, action_id, config, service_config_hash)
VALUES (11, '1', '1', 1, 'service-1-delete-env', '1.0.0', 6, 'SUCCESSFUL',1, '{
    "name": "service-1-delete-env",
    "version": "1.0.0"
}', 'hash1' );





INSERT INTO service_task(id, created_by, updated_by, version, name, service_version, env_id, status, action_id, config, service_config_hash)
VALUES (12, '1', '1', 1, 'service-2-delete-env', '1.0.0', 6, 'SUCCESSFUL',1, '{
    "name": "service-2-delete-env",
    "version": "1.0.0"
}', 'hash1' );



INSERT INTO service_task(id, created_by, updated_by, version, name, service_version, env_id, status, action_id, config, service_config_hash)
VALUES (15, '1', '1', 1, 'service-invalid-status', '1.0.0', 10, 'SUCCESSFUL',5, '{
    "name": "service-invalid-status",
    "version": "1.0.0"
}', 'hash1' );




INSERT INTO component_task(id, version, component_name, service_task_id, config, status, created_by, updated_by, action_id, config_hash, service_account_snapshot)
VALUES (11, 1, "cj-frontend-v1", 11, '{
    "componentConfig": {
        "name": "cj-frontend-v1",
        "type": "application",
        "config": {
            "artifact": {
              "name": "contest-join",
              "version": "1.1.1"
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
}', 'SUCCESSFUL', '1', '1',1,'hash1','{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}');

INSERT INTO component_task(id, version, component_name, service_task_id, config, status, created_by, updated_by, action_id, config_hash, service_account_snapshot)
VALUES (12, 1, "odindemo5", 11, '{
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
}', 'SUCCESSFUL', '1', '1',1,'hash1','{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}' );

INSERT INTO component_task(id, version, component_name, service_task_id, config, status, created_by, updated_by, action_id, config_hash, service_account_snapshot)
VALUES (13, 1, "cj-frontend-v1", 12, '{
    "componentConfig": {
        "name": "cj-frontend-v1",
        "type": "application",
        "config": {
            "artifact": {
              "name": "contest-join",
              "version": "1.1.1"
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
}', 'SUCCESSFUL', '1', '1',1,'hash1','{"account":{"data":{"region":"us-east-1","accountId":"00000000000","description":"AWS account","resourceLabels":{"provisioned-by-user":"${ODIN_USER}","component_name":"${ODIN_COMPONENT_NAME}","service_name":"${ODIN_SERVICE_NAME}","environment_name":"${ODIN_ENV_NAME}","resource_type":"${ODIN_RESOURCE_TYPE}","component_type":"${ODIN_COMPONENT_TYPE}"}},"name":"staging","default":true,"category":"CLOUD","provider":"AWS","services":[{"data":{"clusters":[{"name":"eks-cluster"}]},"name":"EKS","category":"KUBERNETES"},{"name":"EC2","data":{"userData":{"preStart":"","environmentVariables":{}}}}]},"linked_accounts":[]}');
