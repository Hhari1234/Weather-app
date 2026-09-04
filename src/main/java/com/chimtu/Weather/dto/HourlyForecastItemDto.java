package com.chimtu.Weather.dto;

/**
 * One hourly forecast slot (3-hour steps for the next ~24 hours). Temperature is in °C,
 * precipitationProbability is 0..1, precipitationMm and windSpeedMps are metric.
 */
public record HourlyForecastItemDto(
        long epoch,
        double temperatureC,
        double feelsLikeC,
        double precipitationMm,
        double precipitationProbability,
        double windSpeedMps,
        int humidityPercent,
        int cloudinessPercent,
        String iconCode,
        String description) {
}
