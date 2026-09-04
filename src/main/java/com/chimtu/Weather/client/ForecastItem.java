package com.chimtu.Weather.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Raw OpenWeather payload: one 3-hourly entry of the 5-day forecast list. */
public record ForecastItem(
        @JsonProperty("dt") Long dt,
        @JsonProperty("main") MainInfo main,
        @JsonProperty("weather") List<WeatherCondition> weather,
        @JsonProperty("clouds") CloudsInfo clouds,
        @JsonProperty("wind") WindInfo wind,
        @JsonProperty("rain") PrecipInfo rain,
        @JsonProperty("snow") PrecipInfo snow,
        @JsonProperty("pop") Double pop) {
}
