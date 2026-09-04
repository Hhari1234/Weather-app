package com.chimtu.Weather.exception;

/**
 * Signals a failure talking to the external OpenWeather API: timeouts, connection
 * failures, rate limiting, auth problems or unreadable responses.
 *
 * <p>Messages never contain the API key and are only logged server-side; the client only
 * ever sees the generic message chosen by {@link GlobalExceptionHandler}.</p>
 */
public class WeatherApiException extends RuntimeException {

    public enum Failure {
        /** The upstream request exceeded the configured read timeout. */
        TIMEOUT,
        /** Could not establish a connection to the upstream API. */
        CONNECTION,
        /** The upstream API answered with an HTTP error (5xx or unexpected 4xx). */
        UPSTREAM_ERROR,
        /** The upstream API rate-limited us (HTTP 429). */
        RATE_LIMITED,
        /** The configured API key was rejected or missing. */
        AUTH_ERROR,
        /** The upstream response could not be parsed. */
        BAD_RESPONSE
    }

    private final Failure failure;

    public WeatherApiException(Failure failure, String message) {
        super(message);
        this.failure = failure;
    }

    public WeatherApiException(Failure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = failure;
    }

    public Failure getFailure() {
        return failure;
    }
}
