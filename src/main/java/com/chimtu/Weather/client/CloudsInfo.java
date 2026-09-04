package com.chimtu.Weather.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Raw OpenWeather payload: cloudiness in percent. */
public record CloudsInfo(
        @JsonProperty("all") Integer all) {
}
