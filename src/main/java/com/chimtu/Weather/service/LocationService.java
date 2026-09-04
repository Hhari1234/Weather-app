package com.chimtu.Weather.service;

import com.chimtu.Weather.client.GeoLocationPayload;
import com.chimtu.Weather.client.WeatherApiClient;
import com.chimtu.Weather.dto.LocationDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * City/location search backed by the OpenWeather geocoding API. Results are cached for
 * 12 hours because geocoding answers (city -> coordinates) change extremely rarely.
 */
@Service
public class LocationService {

    private static final int MAX_RESULTS = 6;

    private final WeatherApiClient client;

    public LocationService(WeatherApiClient client) {
        this.client = client;
    }

    @Cacheable(cacheNames = "location-search", key = "#query.trim().toLowerCase()")
    public List<LocationDto> search(String query) {
        String normalized = query.trim();
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<LocationDto> results = new ArrayList<>();
        Map<String, LocationDto> seen = new LinkedHashMap<>();
        for (GeoLocationPayload match : client.searchLocations(normalized)) {
            if (match == null || isBlank(match.name())) {
                continue;
            }
            String label = buildLabel(match);
            LocationDto dto = new LocationDto(
                    orEmpty(match.name()),
                    match.state(),
                    orEmpty(match.country()),
                    num(match.lat()),
                    num(match.lon()),
                    label);
            if (seen.putIfAbsent(label, dto) == null) {
                results.add(dto);
            }
            if (results.size() >= MAX_RESULTS) {
                break;
            }
        }
        return results;
    }

    /** "Hyderabad, Telangana, IN" - human readable and a valid OpenWeather q= query. */
    private String buildLabel(GeoLocationPayload match) {
        StringBuilder label = new StringBuilder(match.name());
        if (!isBlank(match.state())) {
            label.append(", ").append(match.state());
        }
        if (!isBlank(match.country())) {
            label.append(", ").append(match.country());
        }
        return label.toString();
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static double num(Double value) {
        return value == null ? 0.0 : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
