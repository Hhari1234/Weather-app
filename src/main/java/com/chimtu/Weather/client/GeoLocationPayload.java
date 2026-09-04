package com.chimtu.Weather.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Raw OpenWeather payload: one result of {@code GET /geo/1.0/direct}. */
public record GeoLocationPayload(
        @JsonProperty("name") String name,
        @JsonProperty("lat") Double lat,
        @JsonProperty("lon") Double lon,
        @JsonProperty("country") String country,
        @JsonProperty("state") String state) {
}
