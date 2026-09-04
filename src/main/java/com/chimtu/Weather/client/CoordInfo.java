package com.chimtu.Weather.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Raw OpenWeather payload: geographic coordinates. */
public record CoordInfo(
        @JsonProperty("lat") Double lat,
        @JsonProperty("lon") Double lon) {
}
