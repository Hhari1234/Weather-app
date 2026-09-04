package com.chimtu.Weather.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Raw OpenWeather payload: a single weather condition ({@code weather[]} entry). */
public record WeatherCondition(
        @JsonProperty("id") Integer id,
        @JsonProperty("main") String main,
        @JsonProperty("description") String description,
        @JsonProperty("icon") String icon) {
}
