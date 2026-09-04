package com.chimtu.Weather.dto;

import java.util.List;

/**
 * Full forecast payload for one location, returned by {@code GET /api/weather/forecast}:
 * hourly slots for the next ~24 hours plus per-calendar-day aggregates derived from
 * OpenWeather's 5-day / 3-hour feed.
 */
public record ForecastDto(
        String city,
        String country,
        long timezoneOffsetSeconds,
        List<HourlyForecastItemDto> hourly,
        List<DailyForecastItemDto> daily) {
}
