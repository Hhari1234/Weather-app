package com.chimtu.Weather.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Simple, production-safe in-memory caching (Caffeine).
 *
 * <p>Repeated lookups of the same city/coordinates are served from cache instead of
 * hitting OpenWeather again. TTLs keep the data fresh without ever being stale for long:
 * current weather 10 minutes, forecast 15 minutes, city search results 12 hours.</p>
 */
@Configuration
@EnableCaching
public class WeatherCacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder().maximumSize(1000));
        cacheManager.registerCustomCache("current-weather", caffeineCache(Duration.ofMinutes(10)));
        cacheManager.registerCustomCache("forecast", caffeineCache(Duration.ofMinutes(15)));
        cacheManager.registerCustomCache("location-search", caffeineCache(Duration.ofHours(12)));
        return cacheManager;
    }

    private Cache<Object, Object> caffeineCache(Duration ttl) {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(ttl)
                .build();
    }
}
