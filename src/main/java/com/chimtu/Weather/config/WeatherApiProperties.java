package com.chimtu.Weather.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenWeather integration settings, bound from the {@code weather.api.*} properties.
 *
 * <p>The API key is bound from the {@code WEATHER_API_KEY} environment variable via
 * {@code weather.api.key=${WEATHER_API_KEY:}} in application.properties. No key is
 * ever hard-coded or committed.</p>
 */
@ConfigurationProperties(prefix = "weather.api")
public record WeatherApiProperties(
        String key,
        String baseUrl,
        Integer connectTimeoutMs,
        Integer readTimeoutMs) {

    public WeatherApiProperties {
        baseUrl = (baseUrl == null || baseUrl.isBlank())
                ? "https://api.openweathermap.org"
                : baseUrl.trim();
        key = (key == null) ? "" : key.trim();
        connectTimeoutMs = (connectTimeoutMs == null || connectTimeoutMs <= 0) ? 4000 : connectTimeoutMs;
        readTimeoutMs = (readTimeoutMs == null || readTimeoutMs <= 0) ? 8000 : readTimeoutMs;
    }

    public boolean hasKey() {
        return !key.isBlank();
    }
}
