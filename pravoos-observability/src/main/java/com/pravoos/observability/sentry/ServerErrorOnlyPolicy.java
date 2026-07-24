package com.pravoos.observability.sentry;

import com.pravoos.common.exception.HttpStatusCarrier;
import io.sentry.SentryEvent;

public class ServerErrorOnlyPolicy implements SentryEventPolicy {

  private static final int MAX_CAUSE_DEPTH = 10;
  private static final int LOWEST_SERVER_ERROR_STATUS = 500;

  @Override
  public boolean shouldReport(SentryEvent event) {
    Throwable throwable = event.getThrowable();
    int depth = 0;
    while (throwable != null && depth < MAX_CAUSE_DEPTH) {
      if (throwable instanceof HttpStatusCarrier carrier) {
        return carrier.httpStatusCode() >= LOWEST_SERVER_ERROR_STATUS;
      }
      throwable = throwable.getCause();
      depth++;
    }
    return true;
  }
}
