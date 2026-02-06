package org.pytenix;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;

public class CaffeineCache {

    private final Cache<String, String> translationCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofHours(1))
            .build();


    public String get(String text, String lang) {
        String cacheKey = lang + ":" + text;
        return translationCache.getIfPresent(cacheKey);
    }

    public void set(String text, String lang, String translated) {
        String cacheKey = lang + ":" + text;
        translationCache.put(cacheKey,translated);
    }


}
