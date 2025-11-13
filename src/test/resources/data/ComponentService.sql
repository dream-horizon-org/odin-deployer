


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


INSERT INTO service_task(id, created_by, updated_by, version, name, service_version, env_id, status, action_id, config, service_config_hash)
VALUES (1, '196', '196', 1, 'service1', '1.0.0', 2, 'IN_PROGRESS',1, '{"key": "value1"}', 'hash1' );

INSERT INTO service_task(id, created_by, updated_by, version, name, service_version, env_id, status, action_id, config, service_config_hash)
VALUES (2, '196', '196', 1, 'service2', '1.0.0', 1, 'SUCCESSFUL', 1, '{"key": "value2"}', 'hash2' );

INSERT INTO component_task(id, version, component_name, service_task_id, config, status, created_by, updated_by, action_id, config_hash)
VALUES (1, 1, "component1s1", 1, '{"depends_on":["component2s1"], "scale":1}', 'IN_PROGRESS', '196', '196',1,'hash1');

INSERT INTO component_task(id, version, component_name, service_task_id, config,status,created_by, updated_by, action_id, config_hash)
VALUES (2, 1, "component2s1",  1, '{"scale":2}', 'SUCCESSFUL', '196', '196', 1,'hash1');

INSERT INTO component_task(id, version, component_name, service_task_id, config, status,created_by, updated_by, action_id, config_hash)
VALUES (3, 1, "component1s2", 2, '{"scale":3}', 'FAILED', '196', '196', 2,'hash1');
