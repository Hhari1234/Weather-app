package com.chimtu.Weather.controller;

import com.chimtu.Weather.dto.CurrentWeatherDto;
import com.chimtu.Weather.dto.ForecastDto;
import com.chimtu.Weather.dto.LocationDto;
import com.chimtu.Weather.service.LocationService;
import com.chimtu.Weather.service.WeatherService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON REST API consumed by the Thymeleaf page (weather.js).
 *
 * <pre>
 *   GET /api/weather?city=Hyderabad
 *   GET /api/weather/forecast?city=Hyderabad
 *   GET /api/weather/coordinates?lat=17.385&lon=78.4867
 *   GET /api/location/search?query=Hyder
 * </pre>
 *
 * <p>Requests are validated; violations produce a 400 with a clean JSON error body via
 * {@link com.chimtu.Weather.exception.GlobalExceptionHandler}. No CORS configuration is
 * needed (and none is configured): the page and the API are served by the same origin.</p>
 */
@RestController
@RequestMapping("/api")
@Validated
public class WeatherApiController {

    private final WeatherService weatherService;
    private final LocationService locationService;

    public WeatherApiController(WeatherService weatherService, LocationService locationService) {
        this.weatherService = weatherService;
        this.locationService = locationService;
    }

    @GetMapping("/weather")
    public CurrentWeatherDto currentWeather(
            @RequestParam("city") @NotBlank(message = "City name is required.")
            @Size(max = 100, message = "City name is too long.") String city) {
        return weatherService.currentByCity(city);
    }

    @GetMapping("/weather/forecast")
    public ForecastDto forecast(
            @RequestParam("city") @NotBlank(message = "City name is required.")
            @Size(max = 100, message = "City name is too long.") String city) {
        return weatherService.forecastByCity(city);
    }

    @GetMapping("/weather/coordinates")
    public CurrentWeatherDto currentWeatherByCoordinates(
            @RequestParam("lat") @DecimalMin(value = "-90", message = "Latitude must be between -90 and 90.")
            @DecimalMax(value = "90", message = "Latitude must be between -90 and 90.") double lat,
            @RequestParam("lon") @DecimalMin(value = "-180", message = "Longitude must be between -180 and 180.")
            @DecimalMax(value = "180", message = "Longitude must be between -180 and 180.") double lon) {
        return weatherService.currentByCoordinates(lat, lon);
    }

    @GetMapping("/location/search")
    public List<LocationDto> searchLocations(
            @RequestParam("query") @NotBlank(message = "Search query is required.")
            @Size(min = 2, max = 100, message = "Search query must be between 2 and 100 characters.") String query) {
        return locationService.search(query);
    }
}
