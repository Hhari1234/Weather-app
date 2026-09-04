package com.chimtu.Weather.exception;

import com.chimtu.Weather.dto.ApiError;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Iterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Central exception handling for the REST API. Every error is converted into a clean
 * {@link ApiError} JSON body with an appropriate HTTP status. Stack traces and internal
 * details are logged server-side only and never exposed to clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CityNotFoundException.class)
    public ResponseEntity<ApiError> handleCityNotFound(CityNotFoundException ex) {
        log.info("City not found: {}", safe(ex.getMessage()));
        return build(HttpStatus.NOT_FOUND, "CITY_NOT_FOUND", "We couldn't find that city. Check the spelling and try again.");
    }

    @ExceptionHandler(WeatherApiException.class)
    public ResponseEntity<ApiError> handleWeatherApi(WeatherApiException ex) {
        log.warn("OpenWeather upstream failure [{}]: {}", ex.getFailure(), safe(ex.getMessage()));
        return switch (ex.getFailure()) {
            case TIMEOUT -> build(HttpStatus.GATEWAY_TIMEOUT, "WEATHER_TIMEOUT",
                    "The weather service took too long to respond. Please try again in a moment.");
            case RATE_LIMITED -> build(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Too many weather requests right now. Please wait a moment and try again.");
            case AUTH_ERROR, CONNECTION, UPSTREAM_ERROR, BAD_RESPONSE -> build(HttpStatus.BAD_GATEWAY,
                    "WEATHER_UNAVAILABLE", "The weather service is temporarily unavailable. Please try again later.");
        };
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        Iterator<ConstraintViolation<?>> iterator = ex.getConstraintViolations().iterator();
        String message = iterator.hasNext() ? iterator.next().getMessage() : "Invalid request parameters.";
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Required query parameter '" + ex.getParameterName() + "' is missing.");
    }

    /** Spring MVC 6.x method-validation (constraints on controller method parameters). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        log.info("Handler method validation failed: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Please check the request parameters and try again.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Query parameter '" + ex.getName() + "' has an invalid value.");
    }

    /** Unknown routes / missing resources -> clean 404 instead of a 500. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
        log.info("Resource not found: {}", ex.getResourcePath());
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested page or resource does not exist.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid request: {}", safe(ex.getMessage()));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request parameters.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong on our side. Please try again later.");
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message));
    }

    private String safe(String value) {
        return value == null ? "unknown" : value.replaceAll("[\\r\\n]+", " ");
    }
}
