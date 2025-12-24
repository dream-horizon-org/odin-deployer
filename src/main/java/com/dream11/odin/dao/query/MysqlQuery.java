package com.dream11.odin.dao.query;

import java.util.function.UnaryOperator;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MysqlQuery {

  public static final String BY_ENVIRONMENT_NAME = " AND e.name = ?";
  public static final String BY_USER = " AND e.created_by = ?";
  public static final String BY_ENVIRONMENT_ID = "e.id=  ?";
  public static final String BY_ACCOUNT = " AND ea.account_name = ?";
  public static final String BY_ORG_ID_AND_ACTIVE = "e.org_id = ? AND e.is_active = 1";
  public static final String EOL = ";";

  public static final String GET_ENVIRONMENTS_WITH_ACCOUNTS_BASE =
      """
    SELECT
      e.org_id,
      e.name,
      e.created_by,
      e.created_at,
      e.updated_at,
      e.updated_by,
      ea.id,
      ea.environment_id,
      ea.status,
      ea.account_name,
      ea.account_data,
      ea.action AS action
    FROM environment AS e JOIN environment_account AS ea ON e.id = ea.environment_id
    WHERE
  """;

  public static final UnaryOperator<String> GET_ACTIVE_ENVIRONMENT_WITH_ACCOUNTS_FOR_ORG =
      query -> GET_ENVIRONMENTS_WITH_ACCOUNTS_BASE + BY_ORG_ID_AND_ACTIVE + query + EOL;

  public static final UnaryOperator<String> GET_ENVIRONMENT_WITH_ACCOUNTS =
      query -> GET_ENVIRONMENTS_WITH_ACCOUNTS_BASE + query + EOL;

  public static final String CREATE_ENVIRONMENT =
      """
    INSERT INTO environment(created_by, org_id, name, updated_by) VALUES (?,?,?,?);
  """;

  public static final String CREATE_ENVIRONMENT_ACCOUNT =
      """
    INSERT INTO environment_account(environment_id, action, status, created_by, account_name, account_data, updated_by)
    VALUES (?,?,?,?,?,?,?);
  """;

  public static final String UPDATE_EXECUTION_TASK =
      """
      UPDATE execution_tasksSET status = ?, response = CAST(? AS JSON)
      WHERE execution_id = ?;
  """;

  public static final String UPDATE_ENVIRONMENT_ACTIVE_STATUS =
      """
  UPDATE environment SET is_active = 0
  WHERE id = (SELECT environment_id FROM environment_account WHERE environment_account.id = ? AND action = ? AND environment_account.status = ?);
  """;

  public static final String UPDATE_ENVIRONMENT_ACCOUNTS =
      """
    UPDATE environment_account SET status=?, action=? WHERE environment_id=?;
   """;

  public static final String UPDATE_ENVIRONMENT_ACCOUNT_STATUS =
      """
    UPDATE environment_account SET status = ? WHERE id = ?
  """;

  public static final String GET_ENVIRONMENT_SERVICES =
      """
    SELECT
      es.id AS service_id,
      es.environment_id,
      es.name AS service_name,
      es.action AS service_action,
      es.status AS service_status,
      es.created_by,
      es.updated_by,
      es.created_at,
      es.updated_at,
      es.config AS service_config
      FROM environment_service AS es
      WHERE es.environment_id=?
      AND NOT (es.action = 'UNDEPLOY' AND es.status = 'SUCCESSFUL');
  """;

  public static final String GET_ENVIRONMENT_SERVICE_COMPONENTS =
      """
    SELECT
      es.environment_id,
      es.name AS service_name,
      es.action AS service_action,
      es.status AS service_status,
      es.created_by AS service_created_by,
      es.updated_by AS service_updated_by,
      es.created_at AS service_created_at,
      es.updated_at AS service_updated_at,
      es.config AS service_config,
      esc.name AS component_name,
      esc.action AS component_action,
      esc.status AS component_status,
      esc.config AS component_config,
      esc.account_data,
      esc.created_by AS component_created_by,
      esc.updated_by AS component_updated_by,
      esc.created_at AS component_created_at,
      esc.updated_at AS component_updated_at
    FROM environment_service AS es LEFT JOIN environment_service_component AS esc ON es.id = esc.environment_service_id
    WHERE es.environment_id=? AND es.name=? AND NOT (es.action = 'UNDEPLOY' AND es.status = 'SUCCESSFUL')
    """; // TODO AKSHAY see if we need to filter undeploy successful components

  public static final String GET_ENVIRONMENT_SERVICE_COMPONENT =
      GET_ENVIRONMENT_SERVICE_COMPONENTS + " AND esc.name=?;";

  public static final String GET_ENV_SERVICE_COMPONENT =
      "SELECT e.name AS environment_name, e.id AS environment_id, s.name AS service_name, "
          + "s.action AS service_action, s.status AS service_status, s.created_by AS created_by,"
          + " s.updated_by AS updated_by, c.name AS component_name,"
          + " c.action AS component_action, c.status AS component_status "
          + "FROM environment e JOIN environment_service s ON e.id = s.environment_id "
          + "JOIN environment_service_component c ON s.id = c.environment_service_id "
          + "WHERE e.org_id = ? AND e.name = ? AND s.name = ?; ";

  // TODO AKSHAY Delete below this
  private static final String SELECT_SERVICE_TASK_FIELDS =
      "SELECT service_task.id AS id, env_id, service_task.name, service_version, "
          + "config, service_config_hash, action.name AS actions, status, service_task.version, "
          + "service_task.created_by, "
          + "service_task.updated_by ";

  public static final String CREATE_COMPONENT_TASK =
      "INSERT INTO component_task "
          + "(service_task_id, component_name, action_id, status, config, config_hash, version, service_account_snapshot, created_by, "
          + "updated_by) "
          + "VALUES (?,?,(SELECT action.id AS action_id FROM action WHERE action.name = ?),?,?,?,?,?,?,?);";

  public static final String CREATE_SERVICE_TASK =
      "INSERT INTO service_task "
          + "(action_id, config, service_config_hash, env_id, name, service_version, status, version, trace_id, created_by, "
          + "updated_by) VALUES (( SELECT action.id AS action_id FROM action WHERE action.name = ?),?,?,?,?,?,?,?,?,?,?);";

  public static final String GET_SERVICE_COMPONENT_TASK_STATUS =
      """
          SELECT st.id              AS service_task_id,
                 st.name            AS service_name,
                 st.service_version AS service_version,
                 ast.name           AS service_task_action,
                 st.status          AS service_task_status,
                 ct.id              AS component_task_id,
                 ct.config          AS component_config,
                 act.name           AS component_task_action,
                 ct.status          AS component_task_status,
                 ct.response        AS component_task_response,
                 ct.component_name  AS component_name
          FROM component_task AS ct
                   JOIN service_task st ON ct.service_task_id = st.id
                   JOIN action act ON ct.action_id = act.id
                   JOIN action ast ON st.action_id = ast.id
          WHERE st.id = ?;
          """;
  public static final String GET_LATEST_NON_HEALTHCHECK_SERVICE_TASK =
      SELECT_SERVICE_TASK_FIELDS
          + "FROM service_task LEFT JOIN action ON action_id=action.id  "
          + "WHERE env_id = ? AND service_task.name = ? AND (action.name IS NULL OR action.name != 'HEALTHCHECK') "
          + "ORDER BY service_task.id DESC "
          + "LIMIT 1;";

  public static final String GET_SERVICE_TASK_BY_TRACE_ID_AND_SERVICE_NAME_AND_ENV =
      SELECT_SERVICE_TASK_FIELDS
          + "FROM service_task  LEFT JOIN action ON action_id=action.id   JOIN environment ev ON service_task.env_id=ev.id "
          + "WHERE trace_id = ?  AND action.name in ('DEPLOY', 'OPERATE') AND service_task.name = ? AND ev.name = ?;";

  public static final String GET_EXECUTION_BY_ID_AND_ACTION =
      "SELECT execution_tasks.id, execution_tasks.action, "
          + "execution_tasks.org_id, execution_tasks.status, execution_tasks.entity,"
          + " execution_tasks.execution_id, execution_tasks.response, "
          + "execution_tasks.payload from execution_tasks where execution_id=? and action.name=?;";

  public static final String UPSERT_ENVIRONMENT_SERVICE =
      "INSERT INTO environment_service ( "
          + "    environment_id, "
          + "    name, "
          + "    action, "
          + "    config, "
          + "    status, "
          + "    created_by, "
          + "    updated_by "
          + ") VALUES ( "
          + "    ?, ?, ?, ?, ?, ?, ? "
          + ") ON DUPLICATE KEY UPDATE "
          + "    action = ?, "
          + "    config = VALUES(config), "
          + "    status = ?, "
          + "    updated_by = VALUES(updated_by) ";

  public static final String UPSERT_ENVIRONMENT_SERVICE_COMPONENT =
      """
INSERT INTO environment_service_component
            (environment_service_id, action, name, status, config, account_data, created_by, updated_by)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
            action = ?,
            status = ?,
            config = VALUES(config),
            account_data = VALUES(account_data),
            updated_by = VALUES(updated_by)
""";

  public static final String GET_ENVIRONMENT_SERVICE_ID =
      "SELECT id FROM environment_service WHERE environment_id = ? AND name = ?";

  public static final String UPDATE_ENVIRONMENT_SERVICE_STATUS =
      "UPDATE environment_service SET status = ? WHERE id = ?;";
  public static final String UPDATE_ENVIRONMENT_SERVICE_COMPONENT_STATUS =
      "UPDATE environment_service_component SET status = ? WHERE name = ? AND environment_service_id = ?;";

  public static final String GET_LATEST_COMPLETED_SERVICE_TASK =
      SELECT_SERVICE_TASK_FIELDS
          + "FROM service_task LEFT JOIN action ON action_id=action.id  "
          + "WHERE env_id = ? AND service_task.name = ? and status in ('FAILED', 'SUCCESSFUL') "
          + "ORDER BY service_task.id DESC "
          + "LIMIT 1;";

  public static final String GET_SERVICE_NAME_AND_ENV_NAME_BY_ID =
      "SELECT  service_task.name as service_name,environment.name as environment_name"
          + " FROM "
          + "service_task  JOIN environment ON env_id =environment.id  WHERE service_task.id = ?";

  public static final String GET_LATEST_SERVICE_TASK_FROM_ENV_AND_ORG =
      SELECT_SERVICE_TASK_FIELDS
          + "FROM service_task LEFT JOIN action ON service_task.action_id=action.id  JOIN environment ON service_task.env_id=environment.id WHERE environment.name "
          + "= ? AND environment.org_id = ? AND service_task.name = ? ORDER BY service_task.id DESC LIMIT 1;";

  public static final String GET_ALL_RUNNING_SERVICE_NAMES_FROM_ENV =
      "SELECT es.name AS service_name, "
          + "es.action AS action_name, "
          + "es.status AS service_status "
          + "FROM environment_service es "
          + "WHERE es.environment_id = ? "
          + "AND NOT (es.action = 'UNDEPLOY' AND es.status = 'SUCCESSFUL') "
          + "AND es.action <> 'HEALTHCHECK' "
          + "ORDER BY es.name;";

  public static final String GET_LATEST_COMPONENT_TASKS =
      "SELECT ct.id, ct.action_id, ct.component_name, ct.status, ct.response, ct.config, "
          + "ct.config_hash, ct.version, ct.created_by, ct.updated_by, action.name as action_name, action.id as action_id, ct"
          + ".service_account_snapshot "
          + "FROM component_task AS ct "
          + "LEFT JOIN action ON action_id=action.id WHERE service_task_id = ?;";

  public static final String UPDATE_COMPONENT_TASK_STATUSES =
      /***
       * TODO: Fix this
       * Context: Currently, If an operate is IN_PROGRESS and another operate is triggered on same service,
       * then the previous operate tasks are also copied in new operate tasks with status IN_PROGRESS.
       * But this causes issues because consumer only updates the previous task and not the new tasks.
       */
      """
                    UPDATE component_task
                    SET status   = ?,
                        response = ?
                    WHERE service_task_id in (SELECT t1.id
                                                FROM service_task t1
                                                  JOIN service_task t2 ON t2.id = ?
                                                    WHERE t1.env_id = t2.env_id
                                                    AND t1.name = t2.name
                                                    AND t1.id >= ?)
                      and component_name = ?
                      and action_id = (SELECT action.id AS action_id FROM action WHERE action.name = ?);
                    """;

  public static final String UPDATE_SERVICE_TASK_STATUS =
      "UPDATE service_task SET status = ? WHERE id = ?;";

  public static final String CREATE_SERVICE_VALIDATE_TASK =
      "INSERT INTO service_validate_task "
          + "(config, service_config_hash, name, service_version, status, version, trace_id, created_by, updated_by) "
          + "VALUES (?,?,?,?,?,?,?,?,?);";

  public static final String UPDATE_SERVICE_VALIDATE_TASK_STATUS =
      "UPDATE service_validate_task SET status = ? WHERE id = ?;";

  public static final String GET_SERVICE_TASK_STATUS_BY_ID =
      "SELECT status FROM service_task WHERE id = ?;";

  public static final String GET_SERVICE_TASK_ID_BY_TRACE_ID =
      "SELECT id FROM service_task WHERE trace_id = ?;";

  public static final String CREATE_COMPONENT_VALIDATE_TASK =
      "INSERT INTO component_validate_task "
          + "(service_validate_task_id, component_name, status, config, config_hash, version, created_by, updated_by) "
          + "VALUES (?,?,?,?,?,?,?,?);";

  public static final String UPDATE_COMPONENT_VALIDATE_TASK_STATUS =
      "UPDATE component_validate_task SET status = ? , response = ?  WHERE service_validate_task_id = ? and component_name = ?;";

  public static final String GET_SERVICE_COMPONENT_VALIDATE_TASK_STATUS =
      """
          SELECT st.id              AS service_validate_task_id,
                 st.name            AS service_name,
                 st.service_version AS service_version,
                 st.status          AS service_validate_task_status,
                 ct.id              AS component_validate_task_id,
                 ct.status          AS component_validate_task_status,
                 ct.response        AS component_validate_task_response,
                 ct.component_name  AS component_name
          FROM component_validate_task AS ct
                   JOIN service_validate_task st ON ct.service_validate_task_id = st.id
          WHERE st.id = ?;
          """;

  public static final String GET_SERVICE_ACTION_STATUS_EXCLUDING_HEALTHCHECK =
      """
                      SELECT service_task.id, service_task.created_at, action.name AS action_name, service_task.status
                      FROM service_task
                               JOIN action ON service_task.action_id = action.id
                      WHERE service_task.name = ?
                        AND env_id = ?
                        AND action.name != 'HEALTHCHECK'
                      ORDER BY created_at DESC
                      LIMIT 1;
                    """;

  public static final String GET_EXISTING_COMPONENTS_STATUSES_EXCLUDING_HEALTHCHECK_IN_ENV =
      """
                    WITH latest_component_tasks AS
                             (SELECT ct.component_name  AS component_name,
                                     a.name             AS action_name,
                                     ct.config          AS component_config,
                                     ct.status          AS component_status,
                                     ROW_NUMBER() OVER (PARTITION BY ct.component_name ORDER BY ct.id DESC) AS rn
                              FROM component_task ct
                                       JOIN service_task st ON ct.service_task_id = st.id
                                       JOIN action a ON ct.action_id = a.id
                              WHERE st.name = ?
                                AND st.env_id = ?
                                AND a.name != 'HEALTHCHECK')
                    SELECT *
                    FROM latest_component_tasks lct
                    WHERE rn = 1;
                        """;

  public static final String GET_EXISTING_COMPONENTS_TASK_IN_ENV =
      """
                    WITH latest_component_tasks AS
                             (SELECT st.id AS service_task_id,
                                     ct.id AS id,
                                     ct.component_name  AS component_name,
                                     a.name             AS action_name,
                                     ct.config          AS config,
                                     ct.config_hash     AS config_hash,
                                     ct.status          AS status,
                                     ct.service_account_snapshot AS service_account_snapshot,
                                     ct.created_by       AS created_by,
                                     ct.updated_by       AS updated_by,
                                     ROW_NUMBER() OVER (PARTITION BY ct.component_name ORDER BY ct.id DESC) AS rn
                              FROM component_task ct
                                       JOIN service_task st ON ct.service_task_id = st.id
                                       JOIN action a ON ct.action_id = a.id
                              WHERE st.name = ?
                                AND st.env_id = ?)
                    SELECT *
                    FROM latest_component_tasks lct
                    WHERE rn = 1;
                        """;

  public static final String GET_LATEST_COMPLETED_DEPLOYED_OR_OPERATED_OR_HEALTHCHECK_SERVICE_TASK =
      """
                    SELECT service_task.id AS id,
                           env_id,
                           service_task.name,
                           service_version,
                           config,
                           service_config_hash,
                           action.name     AS actions,
                           status,
                           service_task.version,
                           service_task.created_by,
                           service_task.updated_by
                    FROM service_task
                             JOIN action ON action_id = action.id
                    WHERE env_id = ?
                      AND service_task.name = ?
                      AND action.name in ('DEPLOY', 'OPERATE', 'HEALTHCHECK')

                    ORDER BY service_task.id DESC
                    LIMIT 1;
                    """;
  public static final String GET_LATEST_SUCCESSFUL_DEPLOY_OPERATE_COMPONENT_TASK_IN_ENV =
      """
                    SELECT  ct.id,
                            ct.component_name,
                            ct.status,
                            ct.config,
                            ct.config_hash,
                            ct.version,
                            ct.created_by,
                            ct.updated_by,
                            action.name AS action_name,
                            action.id   AS action_id,
                            ct.service_account_snapshot
                    FROM component_task AS ct
                             LEFT JOIN action ON action_id = action.id
                             JOIN service_task st ON ct.service_task_id = st.id
                    WHERE st.env_id = ?
                      AND st.name = ?
                      AND ct.component_name = ?
                      AND action.name in ('OPERATE', 'DEPLOY')
                      AND ct.status = 'SUCCESSFUL'
                    ORDER BY ct.id DESC
                    LIMIT 1;
                    """;

  public static final String CREATE_ENVIRONMENT_LOCK_IF_ABSENT =
      "INSERT IGNORE INTO environment_lock (environment_id, created_by, updated_by) VALUES (?, ?, ?);";

  public static final String ACQUIRE_ENVIRONMENT_SHARED =
      "UPDATE environment_lock SET shared_count = shared_count + 1, updated_at = NOW() "
          + "WHERE environment_id = ? AND exclusive = 0;";

  public static final String RELEASE_ENVIRONMENT_SHARED =
      "UPDATE environment_lock SET shared_count = GREATEST(shared_count - 1, 0), updated_at = NOW() "
          + "WHERE environment_id = ?;";

  public static final String ACQUIRE_ENVIRONMENT_EXCLUSIVE =
      "UPDATE environment_lock SET exclusive = 1, updated_at = NOW() "
          + "WHERE environment_id = ? AND exclusive = 0 AND shared_count = 0;";

  public static final String RELEASE_ENVIRONMENT_EXCLUSIVE =
      "UPDATE environment_lock SET exclusive = 0, updated_at = NOW() "
          + "WHERE environment_id = (SELECT environment_id FROM environment_account where id=?) AND exclusive = 1;";

  public static final String CREATE_ENVIRONMENT_SERVICE_LOCK_IF_ABSENT =
      "INSERT IGNORE INTO environment_service_lock (environment_id, environment_service_id, created_by, updated_by) VALUES (?, ?, ?, ?);";

  public static final String ACQUIRE_ENVIRONMENT_SERVICE_SHARED_LOCK =
      "UPDATE environment_service_lock SET shared_count = shared_count + 1, updated_at = NOW() "
          + "WHERE environment_id = ? AND environment_service_id = ? AND exclusive = 0;";

  public static final String RELEASE_ENVIRONMENT_SERVICE_SHARED_LOCK =
      "UPDATE environment_service_lock SET shared_count = GREATEST(shared_count - 1, 0), updated_at = NOW() "
          + "WHERE environment_id = ? AND environment_service_id = ?;";

  public static final String ACQUIRE_ENVIRONMENT_SERVICE_EXCLUSIVE_LOCK =
      "UPDATE environment_service_lock SET exclusive = 1 "
          + "WHERE environment_id = ? AND environment_service_id = ? AND exclusive = 0 AND shared_count = 0;";

  public static final String RELEASE_ENVIRONMENT_SERVICE_EXCLUSIVE_LOCK =
      "UPDATE environment_service_lock SET exclusive = 0, updated_at = NOW() "
          + "WHERE environment_id = ? AND environment_service_id = ? AND exclusive = 1;";

  public static final String CREATE_ENVIRONMENT_SERVICE_COMPONENT_LOCK_IF_ABSENT =
      "INSERT IGNORE INTO environment_service_component_lock (env_id, environment_service_id, environment_service_component_id, created_by, updated_by) VALUES (?, ?, ?, ?, ?);";

  public static final String ACQUIRE_ENVIRONMENT_SERVICE_COMPONENT_SHARED_LOCK =
      "UPDATE environment_service_component_lock SET shared_count = shared_count + 1, updated_at = NOW() "
          + "WHERE env_id = ? AND environment_service_id = ? AND environment_service_component_id = ? AND exclusive = 0;";

  public static final String RELEASE_ENVIRONMENT_SERVICE_COMPONENT_SHARED_LOCK =
      "UPDATE environment_service_component_lock SET shared_count = GREATEST(shared_count - 1, 0), updated_at = NOW() "
          + "WHERE env_id = ? AND environment_service_id = ? AND environment_service_component_id = ?;";

  public static final String ACQUIRE_ENVIRONMENT_SERVICE_COMPONENT_EXCLUSIVE_LOCK =
      "UPDATE environment_service_component_lock SET exclusive = 1, updated_at = NOW() "
          + "WHERE env_id = ? AND environment_service_id = ? AND environment_service_component_id = ? AND exclusive = 0 AND shared_count = 0;";

  public static final String RELEASE_ENVIRONMENT_SERVICE_COMPONENT_EXCLUSIVE_LOCK =
      "UPDATE environment_service_component_lock SET exclusive = 0, updated_at = NOW() "
          + "WHERE env_id = ? AND environment_service_id = ? AND environment_service_component_id = ? AND exclusive = 1;";

  public static final String INSERT_EXECUTION_TASK =
      "INSERT INTO execution_tasks (action, org_id, response, status, entity, execution_id, payload, created_by, updated_by) "
          + "VALUES (?, ?, JSON_OBJECT(), ?, ?, ?, CAST(? AS JSON), ?, ?)";

  public static final String GET_AUTH_PROVIDER_FOR_ORG =
      "SELECT type, provider_details from auth_provider where org_id=?;";
  // Todo - remove oidc specific fields
  public static final String GET_AUTH_PROVIDER_FOR_ORG_WITHOUT_SECRET =
      "SELECT type, JSON_OBJECT("
          + "'name', provider_details->>'$.name', "
          + "'client_id', provider_details->>'$.client_id', "
          + "'authorization_url', provider_details->>'$.authorization_url', "
          + "'token_url', provider_details->>'$.token_url', "
          + "'scope', provider_details->>'$.scope'"
          + ") as provider_details from auth_provider where org_id=?;";
}
