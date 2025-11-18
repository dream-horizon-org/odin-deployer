package com.dream11.odin.dao;

import static com.dream11.odin.dao.query.MysqlQuery.*;
import static com.dream11.odin.error.OdinError.FAILED_TO_ACQUIRE_LOCK;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.client.MysqlClient;
import io.reactivex.Completable;
import io.vertx.reactivex.sqlclient.SqlConnection;
import io.vertx.reactivex.sqlclient.Tuple;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class LockDao {

  private final MysqlClient mysqlClient;

  public Completable ensureEnvironmentLock(long envId, String user, SqlConnection connection) {
    return connection
        .preparedQuery(CREATE_ENVIRONMENT_LOCK_IF_ABSENT)
        .rxExecute(Tuple.of(envId, user, user))
        .ignoreElement();
  }

  public Completable acquireEnvironmentSharedLock(long envId) {
    return mysqlClient
        .getMasterClient()
        .preparedQuery(ACQUIRE_ENVIRONMENT_SHARED)
        .rxExecute(Tuple.of(envId))
        .flatMapCompletable(
            res ->
                res.rowCount() == 1
                    ? Completable.complete()
                    : Completable.error(
                        ExceptionUtil.getException(FAILED_TO_ACQUIRE_LOCK, "environment")));
  }

  public Completable acquireEnvironmentSharedLock(SqlConnection sqlConnection, long envId) {
    return sqlConnection
        .preparedQuery(ACQUIRE_ENVIRONMENT_SHARED)
        .rxExecute(Tuple.of(envId))
        .flatMapCompletable(
            res ->
                res.rowCount() == 1
                    ? Completable.complete()
                    : Completable.error(
                        ExceptionUtil.getException(FAILED_TO_ACQUIRE_LOCK, "environment")));
  }

  public Completable releaseEnvironmentSharedLock(long envId) {
    return mysqlClient
        .getMasterClient()
        .preparedQuery(RELEASE_ENVIRONMENT_SHARED)
        .rxExecute(Tuple.of(envId))
        .ignoreElement();
  }

  public Completable acquireEnvironmentExclusiveLock(long envId, SqlConnection connection) {
    return connection
        .preparedQuery(ACQUIRE_ENVIRONMENT_EXCLUSIVE)
        .rxExecute(Tuple.of(envId))
        .flatMapCompletable(
            res ->
                res.rowCount() == 1
                    ? Completable.complete()
                    : Completable.error(
                        ExceptionUtil.getException(FAILED_TO_ACQUIRE_LOCK, "environment")));
  }

  public Completable releaseEnvironmentExclusiveLock(long envId) {
    return mysqlClient
        .getMasterClient()
        .preparedQuery(RELEASE_ENVIRONMENT_EXCLUSIVE)
        .rxExecute(Tuple.of(envId))
        .ignoreElement();
  }

  public Completable ensureEnvironmentServiceLock(long envId, long serviceId, String user) {
    return mysqlClient
        .getMasterClient()
        .preparedQuery(CREATE_ENVIRONMENT_SERVICE_LOCK_IF_ABSENT)
        .rxExecute(Tuple.of(envId, serviceId, user, user))
        .ignoreElement();
  }

  public Completable ensureEnvironmentServiceLock(
      SqlConnection sqlConnection, long envId, long serviceId, String user) {
    return sqlConnection
        .preparedQuery(CREATE_ENVIRONMENT_SERVICE_LOCK_IF_ABSENT)
        .rxExecute(Tuple.of(envId, serviceId, user, user))
        .ignoreElement();
  }

  public Completable acquireEnvironmentServiceSharedLock(long envId, long serviceId) {
    return mysqlClient
        .getMasterClient()
        .preparedQuery(ACQUIRE_ENVIRONMENT_SERVICE_SHARED_LOCK)
        .rxExecute(Tuple.of(envId, serviceId))
        .flatMapCompletable(
            res ->
                res.rowCount() == 1
                    ? Completable.complete()
                    : Completable.error(
                        ExceptionUtil.getException(FAILED_TO_ACQUIRE_LOCK, "service")));
  }

  public Completable releaseEnvironmentServiceSharedLock(long envId, long serviceId) {
    return mysqlClient
        .getMasterClient()
        .preparedQuery(RELEASE_ENVIRONMENT_SERVICE_SHARED_LOCK)
        .rxExecute(Tuple.of(envId, serviceId))
        .ignoreElement();
  }

  public Completable acquireEnvironmentServiceExclusiveLock(long envId, long serviceId) {
    return mysqlClient
        .getMasterClient()
        .preparedQuery(ACQUIRE_ENVIRONMENT_SERVICE_EXCLUSIVE_LOCK)
        .rxExecute(Tuple.of(envId, serviceId))
        .flatMapCompletable(
            res ->
                res.rowCount() == 1
                    ? Completable.complete()
                    : Completable.error(
                        ExceptionUtil.getException(FAILED_TO_ACQUIRE_LOCK, "service")));
  }

  public Completable acquireEnvironmentServiceExclusiveLock(
      SqlConnection sqlConnection, long envId, long serviceId) {
    return sqlConnection
        .preparedQuery(ACQUIRE_ENVIRONMENT_SERVICE_EXCLUSIVE_LOCK)
        .rxExecute(Tuple.of(envId, serviceId))
        .flatMapCompletable(
            res ->
                res.rowCount() == 1
                    ? Completable.complete()
                    : Completable.error(
                        ExceptionUtil.getException(FAILED_TO_ACQUIRE_LOCK, "service")));
  }

  public Completable releaseEnvironmentServiceExclusiveLock(long envId, long serviceId) {
    return mysqlClient
        .getMasterClient()
        .preparedQuery(RELEASE_ENVIRONMENT_SERVICE_EXCLUSIVE_LOCK)
        .rxExecute(Tuple.of(envId, serviceId))
        .ignoreElement();
  }

  public Completable ensureEnvironmentServiceComponentLock(
      long envId, long serviceId, long componentId, String user) {
    return mysqlClient
        .getMasterClient()
        .preparedQuery(CREATE_ENVIRONMENT_SERVICE_COMPONENT_LOCK_IF_ABSENT)
        .rxExecute(Tuple.of(envId, serviceId, componentId, user, user))
        .ignoreElement();
  }

  public Completable acquireEnvironmentServiceComponentSharedLock(
      long envId, long serviceId, long componentId) {
    return mysqlClient
        .getMasterClient()
        .preparedQuery(ACQUIRE_ENVIRONMENT_SERVICE_COMPONENT_SHARED_LOCK)
        .rxExecute(Tuple.of(envId, serviceId, componentId))
        .flatMapCompletable(
            res ->
                res.rowCount() == 1
                    ? Completable.complete()
                    : Completable.error(
                        ExceptionUtil.getException(FAILED_TO_ACQUIRE_LOCK, "component")));
  }

  public Completable releaseEnvironmentServiceComponentSharedLock(
      long envId, long serviceId, long componentId) {
    return mysqlClient
        .getMasterClient()
        .preparedQuery(RELEASE_ENVIRONMENT_SERVICE_COMPONENT_SHARED_LOCK)
        .rxExecute(Tuple.of(envId, serviceId, componentId))
        .ignoreElement();
  }

  public Completable acquireEnvironmentServiceComponentExclusiveLock(
      long envId, long serviceId, long componentId) {
    return mysqlClient
        .getMasterClient()
        .preparedQuery(ACQUIRE_ENVIRONMENT_SERVICE_COMPONENT_EXCLUSIVE_LOCK)
        .rxExecute(Tuple.of(envId, serviceId, componentId))
        .flatMapCompletable(
            res ->
                res.rowCount() == 1
                    ? Completable.complete()
                    : Completable.error(
                        ExceptionUtil.getException(FAILED_TO_ACQUIRE_LOCK, "component")));
  }

  public Completable releaseEnvironmentServiceComponentExclusiveLock(
      long envId, long serviceId, long componentId) {
    return mysqlClient
        .getMasterClient()
        .preparedQuery(RELEASE_ENVIRONMENT_SERVICE_COMPONENT_EXCLUSIVE_LOCK)
        .rxExecute(Tuple.of(envId, serviceId, componentId))
        .ignoreElement();
  }
}
