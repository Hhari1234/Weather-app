package com.chimtu.Weather.dto;

/**
 * A city/location match from the geocoding search, returned by
 * {@code GET /api/location/search}. {@code displayLabel} is the human readable text shown
 * in the suggestion dropdown and is also a valid OpenWeather query (e.g.
 * "Hyderabad, Telangana, IN").
 */
public record LocationDto(
        String name,
        String state,
        String country,
        double lat,
        double lon,
        String displayLabel) {
}
