package com.dream11.odin.validations;

import com.dream11.odin.util.ValidationUtil;
import io.reactivex.Completable;
import io.reactivex.exceptions.CompositeException;
import io.vertx.json.schema.SchemaRouterOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.json.schema.SchemaParser;
import io.vertx.reactivex.json.schema.SchemaRouter;
import java.util.ArrayList;
import java.util.List;
import lombok.SneakyThrows;

public class Validator {

  final List<Validator> validators = new ArrayList<>();

  public final SchemaParser schemaParser;

  public Validator() {
    SchemaRouter schemaRouter =
        SchemaRouter.create(Vertx.currentContext().owner(), new SchemaRouterOptions());
    this.schemaParser = SchemaParser.createDraft7SchemaParser(schemaRouter);
  }

  public void add(Validator validator) {
    this.validators.add(validator);
  }

  public void add(List<Validator> validators) {
    this.validators.addAll(validators);
  }

  /** Override this method to run validations with validateAll */
  protected Completable validate() {
    return null;
  }

  @SneakyThrows
  public Completable validateAll() {
    if (this.validators.isEmpty()) {
      throw new IllegalArgumentException("No validators found to validate");
    }
    return Completable.mergeDelayError(this.validators.stream().map(Validator::validate).toList())
        .onErrorResumeNext(
            err -> {
              if (err.getClass().equals(CompositeException.class)) {
                return Completable.error(
                    ValidationUtil.buildGrpcFromCompositeException((CompositeException) err));
              } else {
                return Completable.error(err);
              }
            });
  }
}
