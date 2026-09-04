package com.chimtu.Weather.controller;

import java.time.Year;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the single-page dashboard (Thymeleaf template {@code index.html}) that talks to
 * the REST API from {@link WeatherApiController}. There is no separate frontend: the whole
 * application ships as one Spring Boot jar.
 */
@Controller
public class WeatherPageController {

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("year", Year.now().getValue());
        return "index";
    }
}
