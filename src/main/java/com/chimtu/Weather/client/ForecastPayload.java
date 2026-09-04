package com.chimtu.Weather.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Raw OpenWeather payload for {@code GET /data/2.5/forecast} (5 days / 3-hour steps). */
public record ForecastPayload(
        @JsonProperty("city") CityInfo city,
        @JsonProperty("list") List<ForecastItem> list) {
}
