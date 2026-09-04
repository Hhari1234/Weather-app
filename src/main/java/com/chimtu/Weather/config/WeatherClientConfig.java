package com.chimtu.Weather.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP client used for all OpenWeather calls. Timeouts are configured explicitly so a
 * slow or unreachable upstream API can never hang a request (or thread pool) indefinitely.
 */
@Configuration
public class WeatherClientConfig {

    @Bean
    public RestClient weatherRestClient(WeatherApiProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeoutMs());
        requestFactory.setReadTimeout(properties.readTimeoutMs());
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
