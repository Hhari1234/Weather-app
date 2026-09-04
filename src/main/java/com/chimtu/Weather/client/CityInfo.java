package com.chimtu.Weather.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Raw OpenWeather payload: city block returned with the 5-day forecast. */
public record CityInfo(
        @JsonProperty("name") String name,
        @JsonProperty("country") String country,
        @JsonProperty("timezone") Long timezone,
        @JsonProperty("coord") CoordInfo coord) {
}
