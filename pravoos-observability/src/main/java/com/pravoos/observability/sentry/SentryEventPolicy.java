package com.pravoos.observability.sentry;

import io.sentry.SentryEvent;

@FunctionalInterface
public interface SentryEventPolicy {

  boolean shouldReport(SentryEvent event);
}
