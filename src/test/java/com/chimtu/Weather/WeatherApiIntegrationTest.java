package com.chimtu.Weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.chimtu.Weather.client.CityInfo;
import com.chimtu.Weather.client.CloudsInfo;
import com.chimtu.Weather.client.CoordInfo;
import com.chimtu.Weather.client.CurrentWeatherPayload;
import com.chimtu.Weather.client.ForecastItem;
import com.chimtu.Weather.client.ForecastPayload;
import com.chimtu.Weather.client.GeoLocationPayload;
import com.chimtu.Weather.client.MainInfo;
import com.chimtu.Weather.client.SysInfo;
import com.chimtu.Weather.client.WeatherApiClient;
import com.chimtu.Weather.client.WeatherCondition;
import com.chimtu.Weather.client.WindInfo;
import com.chimtu.Weather.exception.CityNotFoundException;
import com.chimtu.Weather.exception.WeatherApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end API tests through the real Spring MVC stack (validation, caching, advice),
 * with the OpenWeather client mocked so no external traffic ever happens.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WeatherApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private WeatherApiClient weatherApiClient;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> {
            if (cacheManager.getCache(name) != null) {
                cacheManager.getCache(name).clear();
            }
        });
    }

    // ------------------------------------------------------------------ page

    @Test
    void homepageRendersThymeleafDashboard() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("brand-accent")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"searchInput\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/css/style.css")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/weather.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/vendor/chart.umd.min.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("©")));
    }

    // ------------------------------------------------------------------ happy paths

    @Test
    void currentWeatherReturnsMappedDto() throws Exception {
        when(weatherApiClient.currentByCity("London")).thenReturn(currentLondon());

        mockMvc.perform(get("/api/weather").param("city", "London"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.city").value("London"))
                .andExpect(jsonPath("$.country").value("GB"))
                .andExpect(jsonPath("$.temperatureC").value(25.4))
                .andExpect(jsonPath("$.feelsLikeC").value(26.1))
                .andExpect(jsonPath("$.humidityPercent").value(60))
                .andExpect(jsonPath("$.pressureHpa").value(1012))
                .andExpect(jsonPath("$.windSpeedMps").value(3.6))
                .andExpect(jsonPath("$.visibilityKm").value(10.0))
                .andExpect(jsonPath("$.description").value("clear sky"))
                .andExpect(jsonPath("$.iconCode").value("01d"))
                .andExpect(jsonPath("$.sunriseEpoch").exists())
                .andExpect(jsonPath("$.dewPointC").exists());
    }

    @Test
    void coordinatesEndpointReturnsCurrentWeather() throws Exception {
        when(weatherApiClient.currentByCoordinates(17.385, 78.4867)).thenReturn(
                currentPayload("Hyderabad", "IN", 17.385, 78.4867));

        mockMvc.perform(get("/api/weather/coordinates")
                        .param("lat", "17.385")
                        .param("lon", "78.4867"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Hyderabad"))
                .andExpect(jsonPath("$.lat").value(17.385));
    }

    @Test
    void forecastReturnsHourlyAndDaily() throws Exception {
        when(weatherApiClient.forecastByCity("London")).thenReturn(forecastFixture(8));

        mockMvc.perform(get("/api/weather/forecast").param("city", "London"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("London"))
                .andExpect(jsonPath("$.country").value("GB"))
                .andExpect(jsonPath("$.hourly").isArray())
                .andExpect(jsonPath("$.hourly.length()").value(8))
                .andExpect(jsonPath("$.hourly[0].temperatureC").exists())
                .andExpect(jsonPath("$.hourly[0].precipitationProbability").value(0.1))
                .andExpect(jsonPath("$.daily").isArray())
                .andExpect(jsonPath("$.daily[0].epoch").exists())
                .andExpect(jsonPath("$.daily[0].temperatureMaxC").exists())
                .andExpect(jsonPath("$.daily[0].precipitationProbability").exists());
    }

    @Test
    void locationSearchReturnsSuggestions() throws Exception {
        when(weatherApiClient.searchLocations("Hyder")).thenReturn(List.of(
                new GeoLocationPayload("Hyderabad", 17.385, 78.4867, "IN", "Telangana"),
                new GeoLocationPayload("Hyderabad", 25.396, 68.377, "PK", "Sindh")));

        mockMvc.perform(get("/api/location/search").param("query", "Hyder"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Hyderabad"))
                .andExpect(jsonPath("$[0].displayLabel").value("Hyderabad, Telangana, IN"))
                .andExpect(jsonPath("$[1].displayLabel").value("Hyderabad, Sindh, PK"));
    }

    // ------------------------------------------------------------------ validation (400s)

    @Test
    void missingCityReturns400() throws Exception {
        mockMvc.perform(get("/api/weather"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void blankCityReturns400() throws Exception {
        mockMvc.perform(get("/api/weather").param("city", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void invalidLatitudeReturns400() throws Exception {
        mockMvc.perform(get("/api/weather/coordinates").param("lat", "91").param("lon", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void nonNumericCoordinatesReturn400() throws Exception {
        mockMvc.perform(get("/api/weather/coordinates").param("lat", "abc").param("lon", "12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void tooShortSearchQueryReturns400() throws Exception {
        mockMvc.perform(get("/api/location/search").param("query", "a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // ------------------------------------------------------------------ error mapping

    @Test
    void unknownCityReturns404JsonWithoutStackTrace() throws Exception {
        when(weatherApiClient.currentByCity("Atlantis"))
                .thenThrow(new CityNotFoundException("OpenWeather: city not found (HTTP 404)"));

        String body = mockMvc.perform(get("/api/weather").param("city", "Atlantis"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CITY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("We couldn't find that city. Check the spelling and try again."))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("stackTrace").doesNotContain("Exception");
    }

    @Test
    void weatherApiTimeoutReturns504() throws Exception {
        when(weatherApiClient.currentByCity("London"))
                .thenThrow(new WeatherApiException(WeatherApiException.Failure.TIMEOUT, "timed out"));

        mockMvc.perform(get("/api/weather").param("city", "London"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error").value("WEATHER_TIMEOUT"));
    }

    @Test
    void weatherApiRateLimitReturns429() throws Exception {
        when(weatherApiClient.currentByCity("London"))
                .thenThrow(new WeatherApiException(WeatherApiException.Failure.RATE_LIMITED, "429"));

        mockMvc.perform(get("/api/weather").param("city", "London"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("RATE_LIMITED"));
    }

    @Test
    void upstreamFailureReturns502GenericBody() throws Exception {
        when(weatherApiClient.currentByCity("London"))
                .thenThrow(new WeatherApiException(WeatherApiException.Failure.UPSTREAM_ERROR, "HTTP 500"));

        String body = mockMvc.perform(get("/api/weather").param("city", "London"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("WEATHER_UNAVAILABLE"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("HTTP 500").doesNotContain("stackTrace");
    }

    @Test
    void unexpectedExceptionReturns500WithoutLeakingInternals() throws Exception {
        when(weatherApiClient.currentByCity("London"))
                .thenThrow(new IllegalStateException("super-secret-internal-detail"));

        String body = mockMvc.perform(get("/api/weather").param("city", "London"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("super-secret-internal-detail");
    }

    // ------------------------------------------------------------------ caching

    @Test
    void repeatedCityLookupIsServedFromCache() throws Exception {
        CurrentWeatherPayload first = currentPayload("CacheVille", "US", 40.0, -74.0);
        CurrentWeatherPayload second = currentPayload("CacheVille", "US", 40.0, -74.0);
        when(weatherApiClient.currentByCity("CacheVille")).thenReturn(first, second);

        // 1st request: cache miss -> upstream
        mockMvc.perform(get("/api/weather").param("city", "CacheVille"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("CacheVille"));
        // 2nd request: must be served from cache - the client is only called once
        mockMvc.perform(get("/api/weather").param("city", "CacheVille"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("CacheVille"));

        verify(weatherApiClient, times(1)).currentByCity("CacheVille");
    }

    // ------------------------------------------------------------- fixtures

    private static CurrentWeatherPayload currentLondon() {
        return currentPayload("London", "GB", 51.51, -0.13);
    }

    private static CurrentWeatherPayload currentPayload(String name, String country, double lat, double lon) {
        return new CurrentWeatherPayload(
                new CoordInfo(lat, lon),
                List.of(new WeatherCondition(800, "Clear", "clear sky", "01d")),
                new MainInfo(25.37, 26.1, 23.9, 27.2, 1012, 60),
                new WindInfo(3.6, 220.0, null),
                new CloudsInfo(5),
                null, null,
                new SysInfo(country, 1700000000L, 1700038000L),
                10000, 1699990000L, name, 0L);
    }

    private static ForecastPayload forecastFixture(int hourlySlots) {
        long now = Instant.now().getEpochSecond();
        long base = ((now / 3600) + 1) * 3600;
        List<ForecastItem> items = new ArrayList<>();
        for (int i = 0; i < hourlySlots; i++) {
            long dt = base + i * 3 * 3600L;
            items.add(new ForecastItem(dt,
                    new MainInfo(20.0 + i, 20.0 + i, 18.0, 22.0, 1010, 60),
                    List.of(new WeatherCondition(800, "Clear", "clear sky", "01d")),
                    new CloudsInfo(10),
                    new WindInfo(4.0, 180.0, null),
                    null, null,
                    0.1 + i * 0.05));
        }
        return new ForecastPayload(
                new CityInfo("London", "GB", 0L, new CoordInfo(51.51, -0.13)),
                items);
    }
}
