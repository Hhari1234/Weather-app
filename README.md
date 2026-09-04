# ⛅ Weather App — Advanced Spring Boot Weather Dashboard

A **production-quality weather dashboard** that ships as **ONE Spring Boot application**:
Thymeleaf renders the page, Spring MVC exposes a JSON REST API, and vanilla JavaScript
(plus a locally vendored Chart.js) renders live OpenWeather data — no React, no Node.js,
no separate frontend or backend.

![Weather App](weather%20app.png)

> The screenshot above shows an early iteration of the app. The current dashboard is a
> single responsive page with current conditions, hourly/daily forecasts and charts —
> see the *Features* section below.

---

## ✨ Features

- **Live current weather** — temperature, feels-like, humidity, wind (direction + speed),
  pressure, visibility, cloudiness, precipitation, dew point, sunrise & sunset
- **Hourly forecast** — the next ~24 hours in 3-hour steps, with precipitation probability
- **Daily forecast cards** — day, date, icon, condition, high/low and rain chance for every
  calendar day present in the feed
- **Three interactive charts** — temperature, precipitation probability (+ mm) and wind
  speed, powered by **Chart.js** (served locally, no CDN dependency at runtime)
- **City search with autocomplete** — debounced suggestions from the OpenWeather geocoding
  API (`Hyderabad, Telangana, IN` style results)
- **📍 Use my location** — browser geolocation; denials and failures degrade gracefully
- **⭐ Favourites & 🕘 Recent searches** — persisted in `localStorage` (click to load, one
  click to remove, history can be cleared)
- **🌙 Dark / ☀️ light mode** — CSS variables, follows the OS preference by default and
  persists your choice
- **°C / °F units** — mathematically correct conversion (and km/h ⇄ mph for wind),
  **purely client-side**: switching units never calls the weather API again
- **Loading skeletons** for every section while Spring Boot fetches data
- **Robust error handling** — invalid cities, empty input, timeouts, rate limiting and
  upstream failures never crash the page; the backend returns clean JSON errors
- **Caching** (Caffeine) — repeated lookups don't hammer OpenWeather
- **Fully responsive** — designed for 375 px phones up to 1920 px desktops
- **100% Spring Boot** — one Maven build produces one runnable jar

---

## 🏗️ Architecture

```text
Browser
   ↓  (HTML/CSS/JS served by Thymeleaf + static resources)
Spring Boot
   ↓
Controller (WeatherPageController / WeatherApiController)
   ↓
Service (WeatherService / LocationService)
   ↓
Client (WeatherApiClient)
   ↓
OpenWeather REST API
```

Package layout (base package `com.chimtu.Weather`):

```text
src/main/java/com/chimtu/Weather
├── WeatherApplication.java        # Spring Boot entry point
├── controller/
│   ├── WeatherPageController.java # GET / (Thymeleaf)
│   └── WeatherApiController.java  # /api/* (JSON REST API, validated)
├── service/
│   ├── WeatherService.java        # mapping + hourly/daily aggregation + @Cacheable
│   └── LocationService.java       # geocoding search + @Cacheable
├── client/
│   ├── WeatherApiClient.java      # OpenWeather HTTP calls, timeouts, error mapping
│   └── *.java                     # typed records matching the OpenWeather payloads
├── dto/                           # API DTOs (CurrentWeatherDto, ForecastDto, …)
├── config/
│   ├── WeatherApiProperties.java  # weather.api.* (key from env var)
│   ├── WeatherClientConfig.java   # RestClient with explicit timeouts
│   └── WeatherCacheConfig.java    # Caffeine cache with per-cache TTLs
└── exception/
    ├── WeatherApiException.java   # timeout / connection / rate-limit / auth / bad response
    ├── CityNotFoundException.java
    └── GlobalExceptionHandler.java# @RestControllerAdvice → clean JSON errors

src/main/resources
├── templates/
│   ├── index.html                 # Thymeleaf dashboard (the whole UI)
│   └── error.html                 # friendly error page for non-API errors
└── static/
    ├── css/style.css              # themes (CSS variables), responsive layout
    ├── js/weather.js              # vanilla JS: data, charts, state, storage
    └── vendor/chart.umd.min.js    # Chart.js v4, vendored (offline-friendly)
```

---

## 🧰 Tech Stack

| Layer        | Technology                                              |
| ------------ | ------------------------------------------------------- |
| Backend      | Java 24, Spring Boot 3.5.x, Spring MVC, Spring Web      |
| Frontend     | Thymeleaf, HTML5, CSS3, vanilla JavaScript, Chart.js 4  |
| HTTP client  | Spring `RestClient` (SimpleClientHttpRequestFactory)    |
| Caching      | Spring Cache abstraction + Caffeine                     |
| Validation   | Jakarta Bean Validation (`spring-boot-starter-validation`) |
| Build        | Maven (Maven Wrapper included)                          |
| Tests        | JUnit 5, Mockito, Spring Boot Test, MockMvc             |
| Data source  | OpenWeather: current weather, 5-day/3-hour forecast, geocoding |

---

## ✅ Requirements

- **Java 24** (the build targets Java 24)
- **Maven 3.9+** — or just use the included wrapper (`./mvnw` on Linux/macOS,
  `mvnw.cmd` on Windows)
- A free **OpenWeather API key**: https://openweathermap.org/api (sign up → *API keys*)

---

## 🔑 Environment Variables

| Variable        | Required | Default | Purpose                                        |
| --------------- | -------- | ------- | ---------------------------------------------- |
| `WEATHER_API_KEY` | **yes** (for live data) | — | Your OpenWeather API key. **Never commit it.** |
| `PORT`          | no       | `8080`  | HTTP port. Render injects this automatically.  |

Optional advanced variables (already defaulted in `application.properties`):

| Variable                | Default                        | Purpose                          |
| ----------------------- | ------------------------------ | -------------------------------- |
| `WEATHER_API_BASEURL`   | `https://api.openweathermap.org` | API base URL (e.g. for proxies) |
| `weather.api.connect-timeout-ms` | `4000`                  | Connect timeout (ms)             |
| `weather.api.read-timeout-ms`    | `8000`                  | Read timeout (ms)                |

For local development copy the example:

```sh
cp .env.example .env        # then fill in your real key
```

`.env` is ignored by Git. You can export the variable in your shell instead:

```bash
# Linux / macOS
export WEATHER_API_KEY=your_api_key_here

# Windows (PowerShell)
$env:WEATHER_API_KEY="your_api_key_here"
```

> ⚠️ **Security:** the API key is read from the `WEATHER_API_KEY` environment variable
> only (`weather.api.key=${WEATHER_API_KEY:}` in `application.properties`). No key is
> hard-coded and nothing secret ever reaches the browser or logs. If you previously had a
> key committed to this repository's Git history, **revoke it in your OpenWeather account**
> and consider rewriting the history before pushing again.

---

## 🚀 Local Setup

```bash
git clone https://github.com/Hhari1234/Weather-app.git
cd Weather-app

# 1. set your API key (see Environment Variables above)
export WEATHER_API_KEY=your_api_key_here

# 2. run the application
./mvnw spring-boot:run
# (Windows: mvnw.cmd spring-boot:run)
```

Open **http://localhost:8080** in your browser.

If you don't set a key yet, the dashboard still loads and every search shows a friendly
"weather service temporarily unavailable" message — set the key and restart to get data.

---

## 🧪 Testing

All tests mock the external API — **no real OpenWeather traffic, no key needed**:

```bash
./mvnw clean test
```

What is covered (JUnit 5 + Mockito + Spring Boot Test + MockMvc):

- `GET /` renders the Thymeleaf dashboard
- `GET /api/weather`, `GET /api/weather/forecast`, `GET /api/weather/coordinates`,
  `GET /api/location/search` — happy paths and DTO shapes
- Invalid input → `400` (missing/blank city, out-of-range lat/lon, non-numeric numbers)
- Unknown city → `404 CITY_NOT_FOUND` (clean JSON, no stack traces)
- Upstream failures → `502 WEATHER_UNAVAILABLE`, timeout → `504`, rate limit → `429`
- Weather API client: URL building, payload mapping, HTTP 404/401/429/5xx mapping
- Service layer: dew-point math, 24 h hourly slicing, calendar-day aggregation
- Caching: a repeated lookup is served from cache (upstream called once)

---

## 🔌 API Endpoints

All endpoints are JSON, served by the same Spring Boot app. Units are metric
(°C, m/s, mm) — the UI converts to °F / km/h / mph without extra calls.

| Endpoint | Description | Success |
| --- | --- | --- |
| `GET /api/weather?city=Hyderabad` | Current weather by city | `200` |
| `GET /api/weather/forecast?city=Hyderabad` | Hourly (24 h) + daily forecast | `200` |
| `GET /api/weather/coordinates?lat=17.385&lon=78.4867` | Current weather by coordinates | `200` |
| `GET /api/location/search?query=Hyder` | Geocoding suggestions | `200` |
| `GET /` | Dashboard (Thymeleaf HTML) | `200` |
| `GET /actuator/health` | Health check (production monitoring) | `200` |

```bash
curl "http://localhost:8080/api/weather?city=Hyderabad"
```

```json
{
  "city": "Hyderabad",
  "country": "IN",
  "lat": 17.385,
  "lon": 78.4867,
  "timezoneOffsetSeconds": 19800,
  "observationEpoch": 1754300000,
  "condition": "Clouds",
  "description": "partly cloudy",
  "iconCode": "03d",
  "temperatureC": 29.3,
  "feelsLikeC": 31.2,
  "temperatureMinC": 27.8,
  "temperatureMaxC": 31.9,
  "humidityPercent": 72,
  "pressureHpa": 1012,
  "windSpeedMps": 3.9,
  "windGustMps": 6.1,
  "windDirectionDeg": 210,
  "visibilityKm": 8.0,
  "cloudinessPercent": 40,
  "precipitationMm": 0.0,
  "dewPointC": 23.8,
  "sunriseEpoch": 1754277600,
  "sunsetEpoch": 1754323800
}
```

### HTTP status codes & error payloads

Errors always use this shape — never stack traces or internal details:

```json
{ "error": "CITY_NOT_FOUND", "message": "We couldn't find that city. Check the spelling and try again." }
```

| HTTP | `error` | Meaning |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | Empty/invalid city, bad lat/lon, bad search query |
| `404` | `CITY_NOT_FOUND` | City unknown to OpenWeather |
| `404` | `NOT_FOUND` | Unknown route / missing resource |
| `429` | `RATE_LIMITED` | OpenWeather rate limit reached |
| `502` | `WEATHER_UNAVAILABLE` | Upstream error, connection failure or bad key config |
| `504` | `WEATHER_TIMEOUT` | Upstream request exceeded the read timeout |
| `500` | `INTERNAL_ERROR` | Unexpected server error (details logged server-side only) |

---

## 🚢 Deployment (Render — one web service)

The whole app — page, API, templates, static assets, Chart.js — is a **single Spring Boot
jar**, so deployment is one web service:

```text
GitHub
   ↓
Render  →  java -jar target/weather-app.jar
   ↓
Spring Boot (Thymeleaf + REST API + static files)
   ↓
OpenWeather API
```

### Option A — blueprint (recommended)

A [`render.yaml`](render.yaml) is included. Push this repo to GitHub, then on Render use
**New → Blueprint** and select the repo. Then:

1. Open the created service → **Environment**.
2. Add `WEATHER_API_KEY` with your real key.
3. Deploy.

The blueprint uses:

```yaml
buildCommand: chmod +x mvnw && ./mvnw -B -DskipTests package
startCommand: java -jar target/weather-app.jar
healthCheckPath: /
```

### Option B — manual

1. **New → Web Service** → connect the GitHub repo.
2. Runtime **Java** (or pick the generic Docker-less Java build, which includes Maven).
3. Build command: `chmod +x mvnw && ./mvnw -B -DskipTests package`
4. Start command: `java -jar target/weather-app.jar`
5. Environment: add `WEATHER_API_KEY`.
6. **HTTP port**: the app binds to `server.port=${PORT:8080}`, so Render's injected
   `PORT` is picked up automatically — no extra configuration.
7. Deploy. Health checks hit `/` which returns `200`.

---

## 📸 Screenshots

The current dashboard is a single responsive page with:

- sticky header: search box (with live suggestions), °C/°F toggle, dark/light toggle,
  **📍 Use my location**
- a current-conditions card with a large temperature, weather icon, feels-like and a grid
  of detail tiles (humidity, wind + compass direction, pressure, visibility, cloudiness,
  precipitation, dew point, sunrise/sunset)
- an hourly strip (`NOW` + next ~24 h) that scrolls horizontally on small screens
- daily forecast cards (day/date, icon, condition, high/low, rain chance)
- temperature, precipitation-probability and wind-speed charts (Chart.js)
- a sidebar with favourites and recent searches

Drop a screenshot of your running instance here when you deploy (`docs/screenshot.png`
and reference it with `![](docs/screenshot.png)`).

---

## 🛠️ Troubleshooting

| Symptom | Fix |
| --- | --- |
| "Weather service is temporarily unavailable" | `WEATHER_API_KEY` is missing/wrong. Set the env var and restart; check the server log for `OpenWeather rejected the API key`. |
| "We couldn't find that city." | OpenWeather doesn't know the query — try the autocomplete or add the country (`London, GB`). |
| App doesn't start | Java 24 required (`java -version`). Use `./mvnw` so the right Maven is downloaded. |
| Port already in use | Set another port: `PORT=9090 ./mvnw spring-boot:run`. |
| No charts | Charts need data for at least a couple of hourly slots (the free feed always provides them). Chart.js ships inside the jar — no internet needed for it. |
| Dark/light looks wrong | The dashboard follows your OS theme until you tap the theme button; your choice is stored in `localStorage`. |
| Why "Next 5–6 days" instead of 7? | The free OpenWeather plan provides a 5-day, 3-hourly forecast (40 points). The app aggregates every available calendar day honestly and shows its count. A paid plan (e.g. One Call) would allow more days. |
| Where is the UV index? | The free OpenWeather feeds don't include UV data, so the UI doesn't invent it. Dew point is computed server-side from temperature + humidity (Magnus formula). |

---

## 📄 License & Data

- Weather data © [OpenWeather](https://openweathermap.org) — check their terms for
  production use/attribution requirements.
- Icons are the official OpenWeather icon set (loaded from their CDN).

---

## 👤 Author

Created and developed by **Hariraj**.

A single Spring Boot application serving the Thymeleaf UI, REST APIs, and integrating with OpenWeather.

## Live Demo

Live Demo: Coming soon — Render deployment
