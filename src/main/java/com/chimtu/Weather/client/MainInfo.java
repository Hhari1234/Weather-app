package com.chimtu.Weather.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Raw OpenWeather payload: the {@code main} block (temperatures, pressure, humidity). */
public record MainInfo(
        @JsonProperty("temp") Double temp,
        @JsonProperty("feels_like") Double feelsLike,
        @JsonProperty("temp_min") Double tempMin,
        @JsonProperty("temp_max") Double tempMax,
        @JsonProperty("pressure") Integer pressure,
        @JsonProperty("humidity") Integer humidity) {
}
