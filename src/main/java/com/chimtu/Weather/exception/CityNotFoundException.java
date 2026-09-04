package com.chimtu.Weather.exception;

/**
 * Thrown when the upstream weather API reports that the requested city/location does not
 * exist. Mapped to HTTP 404 with the CITY_NOT_FOUND error code.
 */
public class CityNotFoundException extends RuntimeException {

    public CityNotFoundException(String message) {
        super(message);
    }
}
