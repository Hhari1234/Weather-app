package com.chimtu.Weather.dto;

/**
 * Current weather, returned by {@code GET /api/weather} and
 * {@code GET /api/weather/coordinates}.
 *
 * <p>All values are metric: temperatures in °C, wind in m/s, precipitation in mm.
 * Epoch fields are Unix seconds; combine them with {@code timezoneOffsetSeconds} on the
 * client to render local time for the location. Units are converted client-side only.</p>
 */
public record CurrentWeatherDto(
        String city,
        String country,
        double lat,
        double lon,
        long timezoneOffsetSeconds,
        long observationEpoch,
        String condition,
        String description,
        String iconCode,
        double temperatureC,
        double feelsLikeC,
        double temperatureMinC,
        double temperatureMaxC,
        int humidityPercent,
        int pressureHpa,
        double windSpeedMps,
        Double windGustMps,
        Double windDirectionDeg,
        Double visibilityKm,
        int cloudinessPercent,
        double precipitationMm,
        Double dewPointC,
        Long sunriseEpoch,
        Long sunsetEpoch) {
}
