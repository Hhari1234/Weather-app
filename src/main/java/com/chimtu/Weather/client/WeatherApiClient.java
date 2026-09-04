package com.chimtu.Weather.client;

import com.chimtu.Weather.config.WeatherApiProperties;
import com.chimtu.Weather.exception.CityNotFoundException;
import com.chimtu.Weather.exception.WeatherApiException;
import com.chimtu.Weather.exception.WeatherApiException.Failure;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Thin HTTP client for the OpenWeather REST API. Every call goes through
 * {@link #exchange(URI, Class)} which converts upstream HTTP errors, timeouts, connection
 * failures and malformed responses into typed domain exceptions. The API key is sent only
 * as a query parameter built from configuration and is never logged or exposed.
 */
@Component
public class WeatherApiClient {

    private static final Logger log = LoggerFactory.getLogger(WeatherApiClient.class);

    private static final String CURRENT_WEATHER_PATH = "/data/2.5/weather";
    private static final String FORECAST_PATH = "/data/2.5/forecast";
    private static final String GEOCODING_PATH = "/geo/1.0/direct";

    private final RestClient restClient;
    private final WeatherApiProperties properties;

    public WeatherApiClient(RestClient weatherRestClient, WeatherApiProperties properties) {
        this.restClient = weatherRestClient;
        this.properties = properties;
    }

    public CurrentWeatherPayload currentByCity(String city) {
        return exchange(apiUri(CURRENT_WEATHER_PATH).queryParam("q", city), CurrentWeatherPayload.class);
    }

    public CurrentWeatherPayload currentByCoordinates(double lat, double lon) {
        return exchange(apiUri(CURRENT_WEATHER_PATH).queryParam("lat", lat).queryParam("lon", lon),
                CurrentWeatherPayload.class);
    }

    public ForecastPayload forecastByCity(String city) {
        return exchange(apiUri(FORECAST_PATH).queryParam("q", city), ForecastPayload.class);
    }

    public List<GeoLocationPayload> searchLocations(String query) {
        return exchangeList(apiUri(GEOCODING_PATH).queryParam("q", query).queryParam("limit", 6));
    }

    private UriComponentsBuilder apiUri(String path) {
        return UriComponentsBuilder.fromHttpUrl(properties.baseUrl())
                .path(path)
                .queryParam("appid", properties.key())
                .queryParam("units", "metric");
    }

    private <T> T exchange(UriComponentsBuilder uriBuilder, Class<T> type) {
        ensureKeyConfigured();
        URI uri = uriBuilder.build().encode().toUri();
        try {
            return restClient.get().uri(uri).retrieve().body(type);
        } catch (HttpClientErrorException e) {
            throw mapClientError(e);
        } catch (HttpServerErrorException e) {
            log.warn("OpenWeather upstream error HTTP {} (no details logged)", e.getStatusCode().value());
            throw new WeatherApiException(Failure.UPSTREAM_ERROR, "OpenWeather returned HTTP " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw mapTransportError(e);
        } catch (HttpMessageConversionException e) {
            throw new WeatherApiException(Failure.BAD_RESPONSE, "OpenWeather returned an unreadable response", e);
        } catch (RestClientException e) {
            throw new WeatherApiException(Failure.UPSTREAM_ERROR, "OpenWeather request failed", e);
        }
    }

    private List<GeoLocationPayload> exchangeList(UriComponentsBuilder uriBuilder) {
        ensureKeyConfigured();
        URI uri = uriBuilder.build().encode().toUri();
        try {
            return restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (HttpClientErrorException e) {
            throw mapClientError(e);
        } catch (HttpServerErrorException e) {
            log.warn("OpenWeather upstream error HTTP {} (no details logged)", e.getStatusCode().value());
            throw new WeatherApiException(Failure.UPSTREAM_ERROR, "OpenWeather returned HTTP " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw mapTransportError(e);
        } catch (RestClientException | HttpMessageConversionException e) {
            throw new WeatherApiException(Failure.UPSTREAM_ERROR, "OpenWeather request failed", e);
        }
    }

    private void ensureKeyConfigured() {
        if (!properties.hasKey()) {
            throw new WeatherApiException(Failure.AUTH_ERROR,
                    "WEATHER_API_KEY is not configured - set it via an environment variable");
        }
    }

    private WeatherApiException mapClientError(HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        if (status == 404) {
            throw new CityNotFoundException("OpenWeather: city not found (HTTP 404)");
        }
        if (status == 401 || status == 403) {
            log.warn("OpenWeather rejected the API key (HTTP {}). Check the WEATHER_API_KEY environment variable.", status);
            throw new WeatherApiException(Failure.AUTH_ERROR, "OpenWeather rejected the API key (HTTP " + status + ")");
        }
        if (status == 429) {
            throw new WeatherApiException(Failure.RATE_LIMITED, "OpenWeather rate limit reached (HTTP 429)");
        }
        throw new WeatherApiException(Failure.UPSTREAM_ERROR, "OpenWeather request failed (HTTP " + status + ")");
    }

    private WeatherApiException mapTransportError(ResourceAccessException e) {
        if (e.getCause() instanceof SocketTimeoutException) {
            log.warn("OpenWeather request timed out");
            return new WeatherApiException(Failure.TIMEOUT, "OpenWeather request timed out", e);
        }
        log.warn("Could not connect to OpenWeather: {}", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        return new WeatherApiException(Failure.CONNECTION, "Could not connect to OpenWeather", e);
    }
}
