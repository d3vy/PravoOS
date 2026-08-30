package com.pravoos.ai.practice.internal.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.pravoos.ai.practice.internal.dto.TemplateResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class TemplateCatalogCache {

  private final Cache<String, List<TemplateResponse>> cache =
      Caffeine.newBuilder().maximumSize(2000).expireAfterWrite(Duration.ofMinutes(2)).build();

  public List<TemplateResponse> get(String key, Function<String, List<TemplateResponse>> loader) {
    return cache.get(key, loader);
  }

  public void evictAll() {
    cache.invalidateAll();
  }
}
