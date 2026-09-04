package com.chimtu.Weather;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies the whole Spring context (controllers, services, client, caching, Thymeleaf)
 * starts up without a real API key. No external calls are made during startup.
 */
@SpringBootTest
class WeatherApplicationTests {

    @Test
    void contextLoads() {
    }
}
