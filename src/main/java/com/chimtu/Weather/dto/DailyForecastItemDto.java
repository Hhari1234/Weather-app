package com.chimtu.Weather.dto;

/**
 * One aggregated calendar-day forecast card. {@code epoch} is the representative slot of
 * the day (used to derive the day name/date client-side), precipitationProbability is the
 * highest per-slot probability observed that day (0..1).
 */
public record DailyForecastItemDto(
        long epoch,
        double temperatureMaxC,
        double temperatureMinC,
        double precipitationMm,
        double precipitationProbability,
        String iconCode,
        String description) {
}
