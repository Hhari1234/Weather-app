package com.chimtu.Weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chimtu.Weather.client.CityInfo;
import com.chimtu.Weather.client.CloudsInfo;
import com.chimtu.Weather.client.CoordInfo;
import com.chimtu.Weather.client.CurrentWeatherPayload;
import com.chimtu.Weather.client.ForecastItem;
import com.chimtu.Weather.client.ForecastPayload;
import com.chimtu.Weather.client.MainInfo;
import com.chimtu.Weather.client.PrecipInfo;
import com.chimtu.Weather.client.SysInfo;
import com.chimtu.Weather.client.WeatherApiClient;
import com.chimtu.Weather.client.WeatherCondition;
import com.chimtu.Weather.client.WindInfo;
import com.chimtu.Weather.dto.CurrentWeatherDto;
import com.chimtu.Weather.dto.ForecastDto;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link WeatherService} mapping and aggregation (Mockito client,
 * no HTTP, no Spring context).
 */
class WeatherServiceTest {

    private WeatherApiClient client;
    private WeatherService service;

    @BeforeEach
    void setUp() {
        client = mock(WeatherApiClient.class);
        service = new WeatherService(client);
    }

    // ---------------------------------------------------------------- current

    @Test
    void mapsCurrentWeatherAndNormalizesCity() {
        when(client.currentByCity("London")).thenReturn(currentPayload());

        CurrentWeatherDto dto = service.currentByCity("  London  ");

        verify(client).currentByCity("London");
        assertThat(dto.city()).isEqualTo("London");
        assertThat(dto.country()).isEqualTo("GB");
        assertThat(dto.temperatureC()).isEqualTo(25.4);   // rounded to 1dp
        assertThat(dto.feelsLikeC()).isEqualTo(26.1);
        assertThat(dto.temperatureMinC()).isEqualTo(23.9);
        assertThat(dto.temperatureMaxC()).isEqualTo(27.2);
        assertThat(dto.humidityPercent()).isEqualTo(60);
        assertThat(dto.pressureHpa()).isEqualTo(1012);
        assertThat(dto.windSpeedMps()).isEqualTo(3.6);
        assertThat(dto.windDirectionDeg()).isEqualTo(220);
        assertThat(dto.visibilityKm()).isEqualTo(10.0);
        assertThat(dto.cloudinessPercent()).isEqualTo(5);
        assertThat(dto.precipitationMm()).isZero();
        assertThat(dto.iconCode()).isEqualTo("01d");
        assertThat(dto.description()).isEqualTo("clear sky");
        assertThat(dto.timezoneOffsetSeconds()).isZero();
        assertThat(dto.sunriseEpoch()).isEqualTo(1700000000L);
        assertThat(dto.sunsetEpoch()).isEqualTo(1700038000L);
        // dew point for 25.4°C / 60% RH is ~17.1°C (Magnus formula)
        assertThat(dto.dewPointC()).isBetween(16.5, 17.6);
    }

    @Test
    void currentByCoordinatesDelegatesWithRoundedValuesAndValidates() {
        when(client.currentByCoordinates(17.385, 78.4867)).thenReturn(currentPayload());

        CurrentWeatherDto dto = service.currentByCoordinates(17.385, 78.4867);

        assertThat(dto.city()).isEqualTo("London"); // fixture
        assertThatThrownBy(() -> service.currentByCoordinates(91.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.currentByCoordinates(0.0, -181.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void visibilityAndDewPointAreNullWhenAbsent() {
        CurrentWeatherPayload payload = new CurrentWeatherPayload(
                null, null, new MainInfo(20.0, 20.0, 19.0, 21.0, null, null),
                null, null, null, null, null, null, 1699990000L, "Nowhere", 0L);
        when(client.currentByCity("Nowhere")).thenReturn(payload);

        CurrentWeatherDto dto = service.currentByCity("Nowhere");

        assertThat(dto.visibilityKm()).isNull();
        assertThat(dto.dewPointC()).isNull();
        assertThat(dto.windSpeedMps()).isZero();
    }

    // --------------------------------------------------------------- forecast

    @Test
    void hourlySliceCoversNext24HoursOnly() {
        long now = Instant.now().getEpochSecond();
        long base = ((now / 3600) + 1) * 3600; // first future 3h-aligned slot
        List<ForecastItem> items = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            items.add(slot(base + i * 3 * 3600L, 20.0 + i, 0.1 + i * 0.1, 3.0 + i));
        }
        items.add(slot(base + 30 * 3600L, 12.0, 0.0, 2.0)); // beyond 24h -> excluded
        items.add(slot(now - 3600, 15.0, 0.0, 1.0));        // in the past -> excluded

        when(client.forecastByCity("London")).thenReturn(
                new ForecastPayload(new CityInfo("London", "GB", 0L, null), items));

        ForecastDto forecast = service.forecastByCity("London");

        assertThat(forecast.city()).isEqualTo("London");
        assertThat(forecast.hourly()).hasSize(8);
        assertThat(forecast.hourly().get(0).epoch()).isEqualTo(base);
        assertThat(forecast.hourly().get(7).epoch()).isEqualTo(base + 21 * 3600L);
        assertThat(forecast.hourly())
                .allMatch(h -> h.epoch() >= now && h.epoch() <= now + 24 * 3600L);
        assertThat(forecast.hourly().get(0).precipitationProbability()).isEqualTo(0.1);
        assertThat(forecast.hourly().get(0).temperatureC()).isEqualTo(20.0);
        assertThat(forecast.hourly().get(0).windSpeedMps()).isEqualTo(3.0);
    }

    @Test
    void dailyAggregationGroupsByLocalDateAndPicksMiddayRepresentative() {
        // Anchor days in the past so the hourly slice can never accidentally match them
        // regardless of the time of day the test runs.
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        long noonDayA = today.minusDays(2).atTime(12, 0).toEpochSecond(ZoneOffset.UTC);
        long noonDayB = today.minusDays(1).atTime(12, 0).toEpochSecond(ZoneOffset.UTC);

        List<ForecastItem> items = List.of(
                // day A: morning, noon, evening
                new ForecastItem(noonDayA - 3 * 3600L, main(20.0),
                        List.of(cond("03d", "scattered clouds")), clouds(40), wind(3.0), null, null, 0.2),
                new ForecastItem(noonDayA, main(26.0),
                        List.of(cond("01d", "clear sky")), clouds(5), wind(4.0),
                        new PrecipInfo(null, 2.0), null, 0.5),
                new ForecastItem(noonDayA + 6 * 3600L, main(23.0),
                        List.of(cond("01d", "clear sky")), clouds(10), wind(5.0), null, null, 0.7),
                // day B: cooler, rainy
                new ForecastItem(noonDayB, main(19.0),
                        List.of(cond("10d", "light rain")), clouds(90), wind(8.0),
                        new PrecipInfo(0.5, null), null, 0.9));

        when(client.forecastByCity("London")).thenReturn(
                new ForecastPayload(new CityInfo("London", "GB", 0L, null), items));

        ForecastDto forecast = service.forecastByCity("London");

        // All fixtures are in the past, so the fallback hourly list contains them - the
        // daily grouping below is what this test focuses on.
        assertThat(forecast.daily()).hasSize(2);

        var dayA = forecast.daily().get(0);
        assertThat(dayA.epoch()).isEqualTo(noonDayA); // midday is the representative
        assertThat(dayA.temperatureMaxC()).isEqualTo(26.0);
        assertThat(dayA.temperatureMinC()).isEqualTo(20.0);
        assertThat(dayA.precipitationMm()).isEqualTo(2.0);
        assertThat(dayA.precipitationProbability()).isEqualTo(0.7); // max pop of the day
        assertThat(dayA.iconCode()).isEqualTo("01d");

        var dayB = forecast.daily().get(1);
        assertThat(dayB.epoch()).isEqualTo(noonDayB);
        assertThat(dayB.temperatureMaxC()).isEqualTo(19.0); // single slot -> hi = lo = its temp
        assertThat(dayB.temperatureMinC()).isEqualTo(19.0);
        assertThat(dayB.precipitationProbability()).isEqualTo(0.9);
        assertThat(dayB.precipitationMm()).isEqualTo(0.5);
        assertThat(dayB.iconCode()).isEqualTo("10d");
    }

    @Test
    void blankCityIsRejected() {
        assertThatThrownBy(() -> service.currentByCity("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------- fixtures

    private static ForecastItem slot(long dt, double temp, double pop, double wind) {
        return new ForecastItem(dt, main(temp),
                List.of(cond("01d", "clear sky")), clouds(0), wind(wind), null, null, pop);
    }

    private static CurrentWeatherPayload currentPayload() {
        return new CurrentWeatherPayload(
                new CoordInfo(51.51, -0.13),
                List.of(cond("01d", "clear sky")),
                new MainInfo(25.37, 26.1, 23.9, 27.2, 1012, 60),
                new WindInfo(3.6, 220.0, null),
                new CloudsInfo(5),
                null, null,
                new SysInfo("GB", 1700000000L, 1700038000L),
                10000, 1699990000L, "London", 0L);
    }

    private static MainInfo main(double temp) {
        return new MainInfo(temp, temp, temp - 1, temp + 1, 1010, 55);
    }

    private static WeatherCondition cond(String icon, String description) {
        return new WeatherCondition(800, "Clear", description, icon);
    }

    private static CloudsInfo clouds(int all) {
        return new CloudsInfo(all);
    }

    private static WindInfo wind(double speed) {
        return new WindInfo(speed, 180.0, null);
    }
}
