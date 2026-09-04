package com.chimtu.Weather.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Raw OpenWeather payload: wind (speed in m/s when units=metric). */
public record WindInfo(
        @JsonProperty("speed") Double speed,
        @JsonProperty("deg") Double deg,
        @JsonProperty("gust") Double gust) {
}
