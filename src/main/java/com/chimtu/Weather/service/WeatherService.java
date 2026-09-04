package com.chimtu.Weather.service;

import com.chimtu.Weather.client.CurrentWeatherPayload;
import com.chimtu.Weather.client.ForecastItem;
import com.chimtu.Weather.client.ForecastPayload;
import com.chimtu.Weather.client.PrecipInfo;
import com.chimtu.Weather.client.WeatherApiClient;
import com.chimtu.Weather.client.WeatherCondition;
import com.chimtu.Weather.dto.CurrentWeatherDto;
import com.chimtu.Weather.dto.DailyForecastItemDto;
import com.chimtu.Weather.dto.ForecastDto;
import com.chimtu.Weather.dto.HourlyForecastItemDto;
import com.chimtu.Weather.exception.WeatherApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Application service for current weather and forecasts.
 *
 * <p>Responsible for translating raw OpenWeather payloads into clean {@code dto} objects,
 * slicing the 3-hourly feed into the "next 24 hours" list and aggregating it into
 * per-calendar-day cards. Results are cached (see {@link WeatherCacheConfig}) so repeated
 * lookups of the same place do not hit OpenWeather again.
 *
 * <p>All outputs are metric (°C, m/s, mm); the UI converts units without re-calling the API.
 */
@Service
public class WeatherService {

    private static final int TWENTY_FOUR_HOURS_SECONDS = 24 * 3600;
    private static final int MAX_HOURLY_ITEMS = 10;
    private static final int MAX_DAILY_ITEMS = 7;

    private final WeatherApiClient client;

    public WeatherService(WeatherApiClient client) {
        this.client = client;
    }

    @Cacheable(cacheNames = "current-weather", key = "#city.trim().toLowerCase()")
    public CurrentWeatherDto currentByCity(String city) {
        return toCurrentDto(client.currentByCity(normalizeCity(city)));
    }

    @Cacheable(cacheNames = "current-weather", key = "#lat + ':' + #lon")
    public CurrentWeatherDto currentByCoordinates(double lat, double lon) {
        validateCoordinates(lat, lon);
        return toCurrentDto(client.currentByCoordinates(lat, lon));
    }

    @Cacheable(cacheNames = "forecast", key = "#city.trim().toLowerCase()")
    public ForecastDto forecastByCity(String city) {
        return toForecastDto(client.forecastByCity(normalizeCity(city)));
    }

    // ------------------------------------------------------------------ mapping

    private CurrentWeatherDto toCurrentDto(CurrentWeatherPayload p) {
        if (p == null) {
            throw new WeatherApiException(WeatherApiException.Failure.BAD_RESPONSE,
                    "OpenWeather returned an empty current-weather response");
        }
        List<WeatherCondition> conditions = p.weather() == null ? List.of() : p.weather();
        WeatherCondition primary = conditions.isEmpty() ? null : conditions.get(0);

        Double humidity = p.main() != null && p.main().humidity() != null ? p.main().humidity().doubleValue() : null;
        Double temp = p.main() != null ? p.main().temp() : null;
        Double dewPoint = (temp != null && humidity != null) ? dewPointCelsius(temp, humidity) : null;

        return new CurrentWeatherDto(
                orEmpty(p.name()),
                p.sys() != null ? orEmpty(p.sys().country()) : "",
                p.coord() != null && p.coord().lat() != null ? p.coord().lat() : 0.0,
                p.coord() != null && p.coord().lon() != null ? p.coord().lon() : 0.0,
                p.timezone() == null ? 0 : p.timezone(),
                p.dt() == null ? 0 : p.dt(),
                primary != null ? orEmpty(primary.main()) : "",
                primary != null ? orEmpty(primary.description()) : "",
                primary != null ? orEmpty(primary.icon()) : "",
                num(p.main() == null ? null : p.main().temp()),
                num(p.main() == null ? null : p.main().feelsLike()),
                num(p.main() == null ? null : p.main().tempMin()),
                num(p.main() == null ? null : p.main().tempMax()),
                p.main() == null || p.main().humidity() == null ? 0 : p.main().humidity(),
                p.main() == null || p.main().pressure() == null ? 0 : p.main().pressure(),
                p.wind() == null || p.wind().speed() == null ? 0.0 : round1(p.wind().speed()),
                p.wind() != null && p.wind().gust() != null ? round1(p.wind().gust()) : null,
                p.wind() != null && p.wind().deg() != null ? p.wind().deg() : null,
                p.visibility() == null ? null : round1(p.visibility() / 1000.0),
                p.clouds() == null || p.clouds().all() == null ? 0 : p.clouds().all(),
                precipMm(p.rain(), p.snow()),
                dewPoint == null ? null : round1(dewPoint),
                p.sys() == null ? null : p.sys().sunrise(),
                p.sys() == null ? null : p.sys().sunset());
    }

    private ForecastDto toForecastDto(ForecastPayload p) {
        if (p == null) {
            throw new WeatherApiException(WeatherApiException.Failure.BAD_RESPONSE,
                    "OpenWeather returned an empty forecast response");
        }
        List<ForecastItem> items = p.list() == null ? List.of() : p.list();
        long offset = p.city() != null && p.city().timezone() != null ? p.city().timezone() : 0L;
        long now = Instant.now().getEpochSecond();

        List<HourlyForecastItemDto> hourly = new ArrayList<>();
        for (ForecastItem item : items) {
            long dt = item.dt() == null ? 0 : item.dt();
            if (dt >= now && dt <= now + TWENTY_FOUR_HOURS_SECONDS) {
                hourly.add(toHourlyDto(item));
                if (hourly.size() >= MAX_HOURLY_ITEMS) {
                    break;
                }
            }
        }
        if (hourly.isEmpty()) {
            // Unusual/aged feed: fall back to the next few entries rather than an empty strip.
            for (ForecastItem item : items) {
                hourly.add(toHourlyDto(item));
                if (hourly.size() >= MAX_HOURLY_ITEMS) {
                    break;
                }
            }
        }

        Map<Long, MutableDay> byDay = new TreeMap<>();
        for (ForecastItem item : items) {
            long dt = item.dt() == null ? 0 : item.dt();
            long localSeconds = dt + offset;
            long dayKey = Math.floorDiv(localSeconds, 86_400L);
            byDay.computeIfAbsent(dayKey, k -> new MutableDay()).add(item, offset);
        }
        List<DailyForecastItemDto> daily = new ArrayList<>();
        for (MutableDay day : byDay.values()) {
            daily.add(day.toDto());
            if (daily.size() >= MAX_DAILY_ITEMS) {
                break;
            }
        }

        return new ForecastDto(
                p.city() != null ? orEmpty(p.city().name()) : "",
                p.city() != null ? orEmpty(p.city().country()) : "",
                offset,
                hourly,
                daily);
    }

    private HourlyForecastItemDto toHourlyDto(ForecastItem item) {
        WeatherCondition condition = firstCondition(item);
        return new HourlyForecastItemDto(
                item.dt() == null ? 0 : item.dt(),
                num(item.main() == null ? null : item.main().temp()),
                num(item.main() == null ? null : item.main().feelsLike()),
                round1(precipMm(item.rain(), item.snow())),
                item.pop() == null ? 0.0 : item.pop(),
                item.wind() == null || item.wind().speed() == null ? 0.0 : round1(item.wind().speed()),
                item.main() == null || item.main().humidity() == null ? 0 : item.main().humidity(),
                item.clouds() == null || item.clouds().all() == null ? 0 : item.clouds().all(),
                condition != null ? orEmpty(condition.icon()) : "",
                condition != null ? orEmpty(condition.description()) : "");
    }

    /** One calendar day accumulator over 3-hourly forecast slots. */
    private static final class MutableDay {
        private double minTemp = Double.POSITIVE_INFINITY;
        private double maxTemp = Double.NEGATIVE_INFINITY;
        private double precipitationMm;
        private double maxPop;
        private ForecastItem representative;
        private long bestNoonDistance = Long.MAX_VALUE;

        void add(ForecastItem item, long offset) {
            Double temp = item.main() == null ? null : item.main().temp();
            if (temp != null) {
                minTemp = Math.min(minTemp, temp);
                maxTemp = Math.max(maxTemp, temp);
            }
            precipitationMm += precipMm(item.rain(), item.snow());
            if (item.pop() != null) {
                maxPop = Math.max(maxPop, item.pop());
            }
            long dt = item.dt() == null ? 0 : item.dt();
            long localHour = Math.floorMod(dt + offset, 86_400L) / 3_600L;
            long distance = Math.abs(localHour - 12);
            if (distance < bestNoonDistance) {
                bestNoonDistance = distance;
                representative = item;
            }
        }

        DailyForecastItemDto toDto() {
            WeatherCondition condition = firstCondition(representative);
            double min = minTemp == Double.POSITIVE_INFINITY ? 0.0 : minTemp;
            double max = maxTemp == Double.NEGATIVE_INFINITY ? 0.0 : maxTemp;
            return new DailyForecastItemDto(
                    representative == null || representative.dt() == null ? 0 : representative.dt(),
                    round1(max),
                    round1(min),
                    round1(precipitationMm),
                    maxPop,
                    condition != null ? orEmpty(condition.icon()) : "",
                    condition != null ? orEmpty(condition.description()) : "");
        }
    }

    // ------------------------------------------------------------ shared helpers

    private static WeatherCondition firstCondition(ForecastItem item) {
        if (item == null || item.weather() == null || item.weather().isEmpty()) {
            return null;
        }
        return item.weather().get(0);
    }

    /** mm of precipitation (rain + snow) for the current/3h interval, 0 when absent. */
    private static double precipMm(PrecipInfo rain, PrecipInfo snow) {
        double total = 0.0;
        if (rain != null) {
            total += firstNonNull(rain.oneHour(), rain.threeHour());
        }
        if (snow != null) {
            total += firstNonNull(snow.oneHour(), snow.threeHour());
        }
        return total;
    }

    private static double firstNonNull(Double a, Double b) {
        return a != null ? a : (b != null ? b : 0.0);
    }

    /**
     * Dew point computed with the Magnus formula (a = 17.625, b = 243.04) from
     * temperature and relative humidity - the standard approximation when the feed
     * does not provide dew point directly.
     */
    private static double dewPointCelsius(double tempC, double humidity) {
        double rh = Math.max(1.0, Math.min(100.0, humidity));
        double a = 17.625;
        double b = 243.04;
        double alpha = Math.log(rh / 100.0) + (a * tempC) / (b + tempC);
        return (b * alpha) / (a - alpha);
    }

    private String normalizeCity(String city) {
        if (city == null || city.trim().isEmpty()) {
            throw new IllegalArgumentException("City name must not be empty");
        }
        return city.trim().replaceAll("\\s{2,}", " ");
    }

    private void validateCoordinates(double lat, double lon) {
        if (lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }
        if (lon < -180.0 || lon > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }
    }

    private static double num(Double value) {
        return round1(value == null ? 0.0 : value);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
