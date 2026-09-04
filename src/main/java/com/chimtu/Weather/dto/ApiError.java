package com.chimtu.Weather.dto;

/**
 * Uniform error payload returned by the REST API, e.g.
 * <pre>{ "error": "CITY_NOT_FOUND", "message": "We couldn't find that city." }</pre>
 * Intentionally free of stack traces, keys or internal server details.
 */
public record ApiError(String error, String message) {
}
