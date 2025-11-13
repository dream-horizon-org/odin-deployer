package com.dream11.odin.dao.query;

import java.util.function.UnaryOperator;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MysqlQuery {

  public static final String BY_ENVIRONMENT_NAME = " AND e.name = ?";
  public static final String BY_USER = " AND e.created_by = ?";
  public static final String BY_ACCOUNT = " AND et.provider_account_name = ?";
  public static final String REMOVE_DELETED_ENVIRONMENTS =
      " AND NOT ( a.name = 'DELETE_ENVIRONMENT' AND et.status = " + "'SUCCESSFUL' ) ";
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
  public static final String CREATE_ENVIRONMENT =
      "INSERT INTO environment(created_by, version, org_id, name, updated_by) VALUES (?,?,?,?,?);";
  public static final String CREATE_ENVIRONMENT_TASK =
      "INSERT INTO environment_task(env_id, action_id, status, version, trace_id, created_by, provider_account_name, "
          + "service_accounts_snapshot, response, updated_by) VALUES (?,?,?,?,?,?,?,?,?,?);";
  public static final String CREATE_SERVICE_TASK =
      "INSERT INTO service_task "
          + "(action_id, config, service_config_hash, env_id, name, service_version, status, version, trace_id, created_by, "
          + "updated_by) VALUES (( SELECT action.id AS action_id FROM action WHERE action.name = ?),?,?,?,?,?,?,?,?,?,?);";
  public static final String EOL = ";";
  public static final String ALL = EOL;
  public static final String GET_ACTION_ID = "SELECT id FROM action WHERE name=?;";

  public static final String GET_ENVIRONMENT_TASK =
      "SELECT id, env_id, version, action_id, status, created_by, provider_account_name, service_accounts_snapshot, response "
          + "FROM environment_task WHERE id = ?"
          + EOL;

  public static final String WITH_SERVICE_TASK_IDS = "WITH service_task_ids AS ";
  public static final String GET_LAST_COMPONENT_TASKS_FOR_SERVICE_IN_ENV =
      WITH_SERVICE_TASK_IDS
          + "("
          + "    SELECT st.id FROM service_task AS st"
          + "    JOIN environment AS env ON st.env_id = env.id"
          + "    WHERE env.id = ? AND st.name = ?"
          + " ),"
          + " ranked_component_task AS ("
          + "    SELECT ct.component_name, ct.config, ct.created_at, ct.updated_at , ct.action_id AS component_action_id, ct.status AS "
          + "component_status,"
          + "    st.name AS service_name, st.service_version AS service_version, st.action_id AS service_action_id, st.status AS service_status,"
          + "    ROW_NUMBER() OVER (PARTITION BY ct.component_name ORDER BY ct.id DESC) AS rn"
          + "    FROM component_task AS ct JOIN service_task AS st ON ct.service_task_id = st.id"
          + "    WHERE st.id IN (SELECT id FROM service_task_ids)"
          + ") "
          + "SELECT ranked_component_task.component_name, ranked_component_task.created_at,ranked_component_task.updated_at,"
          + "ranked_component_task.config, ranked_component_task.component_action_id, "
          + "ranked_component_task.component_status, ranked_component_task.service_name, ranked_component_task.service_version, "
          + "ranked_component_task.service_action_id, ranked_component_task.service_status, action.name AS action_name, rn "
          + "FROM ranked_component_task JOIN action ON ranked_component_task.component_action_id = action.id WHERE rn = 1;";
  public static final String GET_COMPONENT_TASKS_AFTER_LAST_UNDEPLOY_FOR_SERVICE_IN_ENV =
      """
(
    SELECT st.id FROM service_task AS st
    JOIN environment AS env ON st.env_id = env.id
    WHERE env.id = ? AND st.name = ?
),
min_undeploy_id AS (
    SELECT COALESCE(MAX(ct.id), 0) AS ud_id
    FROM component_task AS ct
    JOIN service_task AS st ON ct.service_task_id = st.id
    JOIN action ON ct.action_id = action.id
    WHERE st.id IN (SELECT id FROM service_task_ids)
    AND action.name = 'UNDEPLOY' AND ct.component_name = ?
    AND ct.status = 'SUCCESSFUL'
)
SELECT ct.id, ct.component_name AS component_name, ct.config, ct.action_id AS component_action_id, ct.status AS component_status,
action.name as action_name, ct.created_at as created_at, ct.updated_at as updated_at,
st.name AS service_name, st.service_version AS service_version, st.action_id AS service_action_id, st.status AS service_status
FROM component_task AS ct JOIN service_task AS st ON ct.service_task_id = st.id JOIN action ON ct.action_id = action.id
WHERE st.id IN (SELECT id FROM service_task_ids) AND ct.component_name = ? %s  AND ct.id > (SELECT ud_id FROM min_undeploy_id)
ORDER BY ct.id;
""";

  public static final UnaryOperator<String> GET_COMPONENT_TASKS_FOR_SERVICE_IN_ENV =
      inputQuery -> WITH_SERVICE_TASK_IDS + inputQuery;

  public static final String GET_LATEST_SERVICE_TASKS_FOR_ENV =
      "WITH ranked_service_task AS (SELECT st.id AS service_task_id, st.name, "
          + "st.action_id, st.service_version, st.status, e.org_id, st.env_id, st.created_at,st.updated_at, ROW_NUMBER() OVER "
          + "(PARTITION BY st.name ORDER BY st.id DESC) AS rn FROM service_task AS st join environment AS e ON st.env_id = e.id WHERE st.env_id = ?) "
          + " SELECT ranked_service_task.service_task_id, ranked_service_task.name AS service_name, ranked_service_task.action_id, "
          + " ranked_service_task.env_id, ranked_service_task.service_version AS service_version,ranked_service_task.created_at AS created_at,"
          + "ranked_service_task.updated_at AS updated_at,ranked_service_task.status AS service_status, "
          + " rn, action.name AS action_name "
          + "FROM ranked_service_task JOIN action ON ranked_service_task.action_id = action.id WHERE rn = 1"
          + " AND NOT (action.name ='UNDEPLOY' AND ranked_service_task.status = 'SUCCESSFUL');";

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
      "WITH RankedRows AS (SELECT s.name, a.name as an, s.status, ROW_NUMBER() OVER (PARTITION BY s.name ORDER BY s.id DESC) AS RowNum"
          + " FROM service_task s JOIN action a ON s.action_id = a.id WHERE  NOT (a.name ='UNDEPLOY' AND s.status = 'SUCCESSFUL')"
          + " AND a.name <> 'HEALTHCHECK' and s.env_id = ?) SELECT * FROM RankedRows rr WHERE RowNum = 1 order by rr.name;";

  public static final String GET_LATEST_COMPONENT_TASKS =
      "SELECT ct.id, ct.action_id, ct.component_name, ct.status, ct.response, ct.config, "
          + "ct.config_hash, ct.version, ct.created_by, ct.updated_by, action.name as action_name, action.id as action_id, ct"
          + ".service_account_snapshot "
          + "FROM component_task AS ct "
          + "LEFT JOIN action ON action_id=action.id WHERE service_task_id = ?;";
  public static final String IS_ACTIVE_FILTER = " AND e.is_active = true";
  public static final String GET_FAILED_OR_SUCCESS_SERVICE_COMPONENT_TASKS =
      "SELECT ct.id, ct.action_id, ct.component_name, ct.status, ct.config, ct.config_hash, ct.version, ct.created_by, ct.updated_by, "
          + "action.name as "
          + "action_name, action.id as action_id, ct.service_account_snapshot FROM component_task AS ct LEFT JOIN action ON "
          + "action_id=action.id WHERE "
          + "service_task_id = ? and status in ('SUCCESSFUL', 'FAILED') ORDER BY ct.id ASC;";
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
  public static final String UPDATE_ENVIRONMENT =
      "UPDATE environment SET created_by = ?, version = version+1, org_id = ?, "
          + " name = ? WHERE org_id = ? AND id = ? AND version = ?;";

  public static final String UPDATE_ENVIRONMENT_ACTIVE_STATUS =
      "UPDATE environment SET is_active = 0 WHERE id = (SELECT env_id FROM environment_task JOIN action ac ON environment_task.action_id "
          + "= ac.id  WHERE environment_task.id = ?  AND  ac.name = ? AND environment_task.status = ?)"
          + EOL;

  public static final String UPDATE_ENVIRONMENT_TASK_STATUS =
      "UPDATE environment_task SET status = ?, response = ? WHERE id = ?" + EOL;

  public static final String UPDATE_SERVICE_TASK_STATUS =
      "UPDATE service_task SET status = ? WHERE id = ?;";
  public static final String WITH_RANKED_ENVIRONMENT_TASK = "WITH ranked_environment_task AS ";
  public static final String ENVIRONMENT_BY_ID =
      WITH_RANKED_ENVIRONMENT_TASK
          + "(SELECT environment.id as env_id, environment.created_at, environment.updated_at,environment.updated_by, "
          + "environment"
          + ".created_by, environment"
          + ".version, "
          + "environment.org_id, environment_task.provider_account_name, environment.name, "
          + "environment_task.status, environment_task.action_id, environment_task.service_accounts_snapshot, "
          + "ROW_NUMBER() OVER (PARTITION BY environment_task.provider_account_name ORDER BY environment_task.id DESC) AS rn "
          + "FROM environment JOIN environment_task ON environment.id = environment_task.env_id WHERE environment.id = ? ) "
          + "SELECT ranked_environment_task.env_id as id, ranked_environment_task.env_id as env_id, ranked_environment_task.created_at, "
          + "ranked_environment_task.updated_at,ranked_environment_task.updated_by, "
          + "ranked_environment_task.service_accounts_snapshot, "
          + "ranked_environment_task.created_by, ranked_environment_task.version, ranked_environment_task.org_id, ranked_environment_task.provider_account_name, "
          + "ranked_environment_task.name, ranked_environment_task.status, rn, action.name AS action_name "
          + "FROM ranked_environment_task JOIN action ON ranked_environment_task.action_id = action.id WHERE rn = 1;";

  public static final String ENVIRONMENTS_BY_ORG_WITH_ALL_FIELDS =
      """
SELECT
    e.id AS id,
    e.id AS env_id,
    e.created_at,
    e.updated_at,
    e.created_by,
    e.updated_by,
    e.version,
    e.is_active,
    e.org_id,
    et.provider_account_name,
    et.service_accounts_snapshot,
    e.name,
    et.status,
    a.name AS action_name
FROM
    environment AS e
JOIN (
    SELECT
        et.env_id,
        et.provider_account_name,
        MAX(et.id) AS max_id
    FROM
        environment_task et
    JOIN environment e ON et.env_id = e.id
    WHERE e.org_id = ?
    GROUP BY et.env_id, et.provider_account_name
) AS latest_task ON e.id = latest_task.env_id
JOIN environment_task AS et
    ON et.id = latest_task.max_id
    AND et.provider_account_name = latest_task.provider_account_name
JOIN action AS a ON et.action_id = a.id
WHERE
    e.org_id = ?""";

  public static final UnaryOperator<String> GET_ENVIRONMENTS_WITH_ALL_FIELDS =
      inputQuery -> ENVIRONMENTS_BY_ORG_WITH_ALL_FIELDS + inputQuery;
  public static final String GET_LAST_ENVIRONMENT_TASK =
      WITH_RANKED_ENVIRONMENT_TASK
          + "(SELECT environment_task.id, environment_task.action_id, environment_task.env_id, environment_task.status, "
          + "environment.org_id, environment.name, environment.created_by, environment.created_at,"
          + " environment.updated_at,environment.updated_by, "
          + "environment.version, environment_task.provider_account_name, environment_task.service_accounts_snapshot, "
          + "ROW_NUMBER() OVER (PARTITION BY environment_task.env_id, environment_task.provider_account_name ORDER BY environment_task.id DESC) AS rn "
          + "FROM environment JOIN environment_task ON environment.id = environment_task.env_id WHERE environment.name = ? AND "
          + "environment.org_id = ? and environment.is_active =1 ) "
          + "SELECT ranked_environment_task.id, ranked_environment_task.action_id, ranked_environment_task.env_id, ranked_environment_task.status, "
          + "ranked_environment_task.org_id, ranked_environment_task.name, ranked_environment_task.created_by, "
          + "ranked_environment_task.provider_account_name, ranked_environment_task.service_accounts_snapshot, "
          + "ranked_environment_task.created_at, ranked_environment_task.updated_at,ranked_environment_task.updated_by, "
          + "ranked_environment_task.version, rn, action.name AS action_name "
          + "FROM ranked_environment_task JOIN action ON ranked_environment_task.action_id = action.id WHERE rn = 1";

  public static final String GET_LAST_ENVIRONMENT_TASK_EXCLUDING_DELETED =
      WITH_RANKED_ENVIRONMENT_TASK
          + "(SELECT environment_task.id, environment_task.action_id, environment_task.env_id, environment_task.status, "
          + "environment.org_id, environment.name, environment.created_by, environment.created_at, environment.updated_at,environment"
          + ".updated_by, "
          + "environment.version, environment_task.provider_account_name, environment_task.service_accounts_snapshot, "
          + "ROW_NUMBER() OVER (PARTITION BY environment_task.env_id, environment_task.provider_account_name ORDER BY environment_task.id DESC) AS rn "
          + "FROM environment JOIN environment_task ON environment.id = environment_task.env_id WHERE environment.name = ? AND environment.org_id = ? ) "
          + "SELECT ranked_environment_task.id, ranked_environment_task.action_id, ranked_environment_task.env_id, ranked_environment_task.status, "
          + "ranked_environment_task.org_id, ranked_environment_task.name, ranked_environment_task.created_by, "
          + "ranked_environment_task.provider_account_name, ranked_environment_task.service_accounts_snapshot, "
          + "ranked_environment_task.created_at, ranked_environment_task.updated_at,ranked_environment_task.updated_by, "
          + "ranked_environment_task.version, rn, action.name AS action_name "
          + "FROM ranked_environment_task JOIN action ON ranked_environment_task.action_id = action.id WHERE rn = 1 AND NOT (action.name "
          + "= 'DELETE_ENVIRONMENT' AND ranked_environment_task.status = 'SUCCESSFUL')";

  public static final String GET_LATEST_ENVIRONMENT_TASK = GET_LAST_ENVIRONMENT_TASK + EOL;
  public static final String GET_LATEST_ENVIRONMENT_TASK_EXCLUDING_DELETED =
      GET_LAST_ENVIRONMENT_TASK_EXCLUDING_DELETED + EOL;

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
