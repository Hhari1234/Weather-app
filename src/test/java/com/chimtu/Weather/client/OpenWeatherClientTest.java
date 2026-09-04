package com.chimtu.Weather.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.chimtu.Weather.config.WeatherApiProperties;
import com.chimtu.Weather.exception.CityNotFoundException;
import com.chimtu.Weather.exception.WeatherApiException;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Exercises the HTTP client against a mock server - no real OpenWeather traffic.
 * Verifies request URLs/params, payload deserialization and the mapping of upstream
 * HTTP errors (404 city, 401 key, 429 rate limit, 5xx) into domain exceptions.
 */
class OpenWeatherClientTest {

    private static final String CURRENT_JSON = """
            {
              "coord": { "lat": 51.51, "lon": -0.13 },
              "weather": [ { "id": 800, "main": "Clear", "description": "clear sky", "icon": "01d" } ],
              "main": {
                "temp": 25.37, "feels_like": 26.1, "temp_min": 23.9, "temp_max": 27.2,
                "pressure": 1012, "humidity": 60
              },
              "visibility": 10000,
              "wind": { "speed": 3.6, "deg": 220 },
              "clouds": { "all": 5 },
              "sys": { "country": "GB", "sunrise": 1700000000, "sunset": 1700038000 },
              "dt": 1699990000,
              "timezone": 0,
              "name": "London"
            }
            """;

    private MockRestServiceServer server;
    private WeatherApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new WeatherApiClient(builder.build(), new WeatherApiProperties("test-key", null, null, null));
    }

    @Test
    void currentByCityBuildsUrlAndMapsPayload() {
        server.expect(requestTo(Matchers.containsString("/data/2.5/weather")))
                .andExpect(queryParam("q", "London"))
                .andExpect(queryParam("units", "metric"))
                .andExpect(queryParam("appid", "test-key"))
                .andRespond(withSuccess(CURRENT_JSON, MediaType.APPLICATION_JSON));

        CurrentWeatherPayload payload = client.currentByCity("London");

        server.verify();
        assertThat(payload.name()).isEqualTo("London");
        assertThat(payload.sys().country()).isEqualTo("GB");
        assertThat(payload.main().temp()).isEqualTo(25.37);
        assertThat(payload.main().humidity()).isEqualTo(60);
        assertThat(payload.weather().get(0).icon()).isEqualTo("01d");
        assertThat(payload.wind().speed()).isEqualTo(3.6);
        assertThat(payload.visibility()).isEqualTo(10000);
        assertThat(payload.timezone()).isZero();
    }

    @Test
    void currentByCoordinatesIncludesLatLonParams() {
        server.expect(requestTo(Matchers.containsString("/data/2.5/weather")))
                .andExpect(queryParam("lat", "17.385"))
                .andExpect(queryParam("lon", "78.4867"))
                .andExpect(queryParam("units", "metric"))
                .andRespond(withSuccess(CURRENT_JSON, MediaType.APPLICATION_JSON));

        client.currentByCoordinates(17.385, 78.4867);

        server.verify();
    }

    @Test
    void forecastMapsPayload() {
        String forecastJson = """
                {
                  "city": { "name": "London", "country": "GB", "timezone": 0,
                            "coord": { "lat": 51.51, "lon": -0.13 } },
                  "list": [
                    {
                      "dt": 1700000000,
                      "main": { "temp": 20.1, "feels_like": 19.5, "temp_min": 18.8, "temp_max": 21.0,
                                "pressure": 1010, "humidity": 55 },
                      "weather": [ { "id": 802, "main": "Clouds", "description": "scattered clouds", "icon": "03d" } ],
                      "clouds": { "all": 40 },
                      "wind": { "speed": 4.1, "deg": 180 },
                      "rain": { "3h": 0.5 },
                      "pop": 0.35
                    }
                  ]
                }
                """;
        server.expect(requestTo(Matchers.containsString("/data/2.5/forecast")))
                .andExpect(queryParam("q", "London"))
                .andRespond(withSuccess(forecastJson, MediaType.APPLICATION_JSON));

        ForecastPayload payload = client.forecastByCity("London");

        server.verify();
        assertThat(payload.city().name()).isEqualTo("London");
        assertThat(payload.list()).hasSize(1);
        ForecastItem item = payload.list().get(0);
        assertThat(item.dt()).isEqualTo(1700000000L);
        assertThat(item.main().temp()).isEqualTo(20.1);
        assertThat(item.rain().threeHour()).isEqualTo(0.5);
        assertThat(item.pop()).isEqualTo(0.35);
    }

    @Test
    void searchLocationsMapsArrayPayload() {
        String geoJson = """
                [
                  { "name": "Hyderabad", "lat": 17.3850, "lon": 78.4867,
                    "country": "IN", "state": "Telangana" },
                  { "name": "Hyderabad", "lat": 25.3960, "lon": 68.3770,
                    "country": "PK", "state": "Sindh" }
                ]
                """;
        server.expect(requestTo(Matchers.containsString("/geo/1.0/direct")))
                .andExpect(queryParam("q", "Hyder"))
                .andRespond(withSuccess(geoJson, MediaType.APPLICATION_JSON));

        List<GeoLocationPayload> results = client.searchLocations("Hyder");

        server.verify();
        assertThat(results).hasSize(2);
        assertThat(results.get(0).name()).isEqualTo("Hyderabad");
        assertThat(results.get(0).country()).isEqualTo("IN");
        assertThat(results.get(1).state()).isEqualTo("Sindh");
    }

    @Test
    void http404MapsToCityNotFoundException() {
        server.expect(requestTo(Matchers.containsString("/data/2.5/weather")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"cod\":\"404\",\"message\":\"city not found\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.currentByCity("Atlantis"))
                .isInstanceOf(CityNotFoundException.class);
        server.verify();
    }

    @Test
    void http401MapsToAuthFailure() {
        server.expect(requestTo(Matchers.containsString("/data/2.5/weather")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"cod\":401,\"message\":\"Invalid API key\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.currentByCity("London"))
                .isInstanceOf(WeatherApiException.class)
                .satisfies(e -> assertThat(((WeatherApiException) e).getFailure())
                        .isEqualTo(WeatherApiException.Failure.AUTH_ERROR));
        server.verify();
    }

    @Test
    void http429MapsToRateLimited() {
        server.expect(requestTo(Matchers.containsString("/data/2.5/weather")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"message\":\"rate limit\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.currentByCity("London"))
                .isInstanceOf(WeatherApiException.class)
                .satisfies(e -> assertThat(((WeatherApiException) e).getFailure())
                        .isEqualTo(WeatherApiException.Failure.RATE_LIMITED));
        server.verify();
    }

    @Test
    void http500MapsToUpstreamError() {
        server.expect(requestTo(Matchers.containsString("/data/2.5/weather")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"message\":\"boom\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.currentByCity("London"))
                .isInstanceOf(WeatherApiException.class)
                .satisfies(e -> assertThat(((WeatherApiException) e).getFailure())
                        .isEqualTo(WeatherApiException.Failure.UPSTREAM_ERROR));
        server.verify();
    }

    @Test
    void missingApiKeyFailsFastWithoutCallingOut() {
        RestClient.Builder builder = RestClient.builder();
        WeatherApiClient noKeyClient =
                new WeatherApiClient(builder.build(), new WeatherApiProperties("", null, null, null));

        assertThatThrownBy(() -> noKeyClient.currentByCity("London"))
                .isInstanceOf(WeatherApiException.class)
                .satisfies(e -> assertThat(((WeatherApiException) e).getFailure())
                        .isEqualTo(WeatherApiException.Failure.AUTH_ERROR));
    }
}
