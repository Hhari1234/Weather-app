package com.chimtu.Weather.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Raw OpenWeather payload: rain or snow volume, keyed by the 1h/3h interval. */
public record PrecipInfo(
        @JsonProperty("1h") Double oneHour,
        @JsonProperty("3h") Double threeHour) {
}
