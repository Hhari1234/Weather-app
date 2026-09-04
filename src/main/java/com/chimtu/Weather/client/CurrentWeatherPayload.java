package com.chimtu.Weather.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Raw OpenWeather payload for {@code GET /data/2.5/weather}. */
public record CurrentWeatherPayload(
        @JsonProperty("coord") CoordInfo coord,
        @JsonProperty("weather") List<WeatherCondition> weather,
        @JsonProperty("main") MainInfo main,
        @JsonProperty("wind") WindInfo wind,
        @JsonProperty("clouds") CloudsInfo clouds,
        @JsonProperty("rain") PrecipInfo rain,
        @JsonProperty("snow") PrecipInfo snow,
        @JsonProperty("sys") SysInfo sys,
        @JsonProperty("visibility") Integer visibility,
        @JsonProperty("dt") Long dt,
        @JsonProperty("name") String name,
        @JsonProperty("timezone") Long timezone) {
}
