package com.dream11.odin.service.operation;

import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.dao.ComponentTaskDao;
import com.dream11.odin.dao.ServiceTaskDao;
import com.dream11.odin.service.ComponentEnrichmentService;
import com.dream11.odin.service.DatabasePollerService;
import com.dream11.odin.service.InterceptorService;
import com.dream11.odin.service.PlaceholderService;
import com.dream11.queue.producer.MessageProducer;
import com.google.inject.Inject;

public abstract class ServiceOperation extends Operation {
  @Inject
  protected ServiceOperation(
      ServiceTaskDao serviceTaskDao,
      ComponentTaskDao componentTaskDao,
      MysqlClient mysqlClient,
      DatabasePollerService databasePollerService,
      MessageProducer<String> messageProducer,
      PlaceholderService placeholderService,
      ComponentEnrichmentService componentEnrichmentService,
      InterceptorService interceptorService) {
    super(
        serviceTaskDao,
        componentTaskDao,
        mysqlClient,
        databasePollerService,
        messageProducer,
        placeholderService,
        componentEnrichmentService,
        interceptorService);
  }
}
