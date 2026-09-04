package com.chimtu.Weather.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Raw OpenWeather payload: system data (country code, sunrise/sunset epochs). */
public record SysInfo(
        @JsonProperty("country") String country,
        @JsonProperty("sunrise") Long sunrise,
        @JsonProperty("sunset") Long sunset) {
}
