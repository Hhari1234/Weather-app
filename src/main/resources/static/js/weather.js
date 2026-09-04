/* ==========================================================================
 * Weather App - vanilla JS dashboard (no framework).
 * Talks only to the Spring Boot REST API on the same origin:
 *   GET /api/weather?city=              GET /api/weather/forecast?city=
 *   GET /api/weather/coordinates?lat=&lon=   GET /api/location/search?query=
 * State (theme, unit, favourites, recents) lives in localStorage; unit/theme
 * switches re-render locally without calling the weather API again.
 * ========================================================================== */
'use strict';

(function () {
    const $ = (sel, root) => (root || document).querySelector(sel);
    const $$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));

    const STORE = {
        theme: 'weather.theme',
        unit: 'weather.units',
        favs: 'weather.favs.v1',
        recents: 'weather.recent.v1',
    };

    const state = {
        unit: 'C', // 'C' or 'F' - display only, never re-fetched
        favs: [],
        recents: [],
        last: null, // { current, forecast, label }
        loadSeq: 0,
        chartLib: window.Chart || null,
        charts: { temp: null, precip: null, wind: null },
        themeAuto: null, // resolves when 'system' is chosen
    };

    /* ------------------------------------------------------------------ storage */

    function storeGet(key, fallback) {
        try {
            const raw = localStorage.getItem(key);
            return raw === null ? fallback : JSON.parse(raw);
        } catch (e) {
            return fallback;
        }
    }

    function storeSet(key, value) {
        try {
            localStorage.setItem(key, JSON.stringify(value));
        } catch (e) {
            /* private mode / quota - non fatal */
        }
    }

    function storeRemove(key) {
        try {
            localStorage.removeItem(key);
        } catch (e) { /* ignore */ }
    }

    /* ------------------------------------------------------------------ dom helpers */

    function el(tag, attrs, children) {
        const node = document.createElement(tag);
        if (attrs) {
            for (const [key, value] of Object.entries(attrs)) {
                if (value === false || value === null || value === undefined) continue;
                if (key === 'class') node.className = value;
                else if (key === 'text') node.textContent = value;
                else if (key === 'html') node.innerHTML = value;
                else if (key.startsWith('on') && typeof value === 'function') {
                    node.addEventListener(key.slice(2), value);
                } else node.setAttribute(key, value === true ? '' : String(value));
            }
        }
        (children || []).forEach((child) => {
            if (child === null || child === undefined) return;
            node.append(child);
        });
        return node;
    }

    const pad2 = (n) => String(n).padStart(2, '0');

    /* ------------------------------------------------------------------ formatting */

    const titleCase = (s) =>
        !s ? '' : s.charAt(0).toUpperCase() + s.slice(1);

    const round = (n, dp = 0) => {
        const f = Math.pow(10, dp);
        return Math.round(n * f) / f;
    };

    // Temperature conversion: the API always returns °C; °F is derived locally.
    const toDisplayC = (c) => (state.unit === 'C' ? c : c * 9 / 5 + 32);
    const tempText = (c) => `${Math.round(toDisplayC(c))}°`;
    const feelsText = (c) => `${Math.round(toDisplayC(c))}°`;

    // Wind: metric API gives m/s -> km/h (°C mode) or mph (°F mode).
    const windPerUnit = () => (state.unit === 'C' ? 3.6 : 2.236936);
    const windText = (mps) => `${Math.round(mps * windPerUnit())}`;
    const windUnit = () => (state.unit === 'C' ? 'km/h' : 'mph');
    const tempUnit = () => (state.unit === 'C' ? '°C' : '°F');

    const COMPASS = ['N', 'NNE', 'NE', 'ENE', 'E', 'ESE', 'SE', 'SSE',
        'S', 'SSW', 'SW', 'WSW', 'W', 'WNW', 'NW', 'NNW'];

    const compassOf = (deg) => {
        if (deg === null || deg === undefined || isNaN(deg)) return '';
        return COMPASS[Math.round(((deg % 360) + 360) % 360 / 22.5) % 16];
    };

    const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
    const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
        'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

    // Render "local time at the reported place" using the API timezone offset.
    function localDate(epoch, offset) {
        return new Date((epoch + (offset || 0)) * 1000);
    }

    const timeText = (epoch, offset) => {
        const d = localDate(epoch, offset);
        return `${pad2(d.getUTCHours())}:${pad2(d.getUTCMinutes())}`;
    };

    const dayNumber = (epoch, offset) =>
        Math.floor((epoch + (offset || 0)) / 86400);

    const dateText = (epoch, offset) => {
        const d = localDate(epoch, offset);
        return `${d.getUTCDate()} ${MONTHS[d.getUTCMonth()]}`;
    };

    const dayLabel = (epoch, offset) => DAYS[localDate(epoch, offset).getUTCDay()];

    const nowDayNumber = () => dayNumber(Date.now() / 1000, state.last
        ? state.last.current.timezoneOffsetSeconds : 0);

    const formatEpochForToday = (epoch, offset) => {
        if (dayNumber(epoch, offset) === nowDayNumber()) return 'Today';
        const d = localDate(epoch, offset);
        return `${DAYS[d.getUTCDay()]} ${d.getUTCDate()}`;
    };

    const ICON_BASE = 'https://openweathermap.org/img/wn/';
    const FALLBACK_ICON = 'data:image/svg+xml;utf8,' + encodeURIComponent(
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><text y="0.9em" font-size="90">⛅</text></svg>');

    function iconImg(code, cls, size) {
        const img = document.createElement('img');
        img.className = cls || '';
        img.alt = '';
        img.loading = 'lazy';
        img.width = size || 48;
        img.height = size || 48;
        const src = code ? ICON_BASE + code + '@2x.png' : FALLBACK_ICON;
        img.src = src;
        img.addEventListener('error', () => { img.src = FALLBACK_ICON; });
        return img;
    }

    function heroIconImg(code) {
        const img = document.createElement('img');
        img.className = 'hero-icon';
        img.id = 'heroIcon';
        img.alt = '';
        img.loading = 'eager';
        img.width = 120;
        img.height = 120;
        const src = code ? ICON_BASE + code + '@4x.png' : FALLBACK_ICON;
        img.src = src;
        img.addEventListener('error', () => { img.src = FALLBACK_ICON; });
        return img;
    }

    /* ------------------------------------------------------------------ toasts */

    function toast(message, kind, ms) {
        const region = $('#toastRegion');
        if (!region) return;
        const icons = { error: '⚠️', info: 'ℹ️', success: '✅' };
        const item = el('div', { class: 'toast is-' + (kind || 'info') }, [
            el('span', { class: 'toast-icon', text: icons[kind] || icons.info }),
            el('span', { text: message }),
            el('button', {
                class: 'toast-close', 'aria-label': 'Dismiss',
                text: '✕', onclick: () => item.remove(),
            }),
        ]);
        while (region.children.length >= 3) region.firstChild.remove();
        region.append(item);
        setTimeout(() => {
            item.style.opacity = '0';
            item.style.transition = 'opacity .3s';
            setTimeout(() => item.remove(), 350);
        }, ms || 5000);
    }

    /* ------------------------------------------------------------------ api */

    async function apiJSON(path, signal) {
        let response;
        try {
            response = await fetch(path, { signal, headers: { Accept: 'application/json' } });
        } catch (err) {
            if (err && err.name === 'AbortError') throw localError('TIMEOUT');
            throw localError('NETWORK');
        }
        let body = null;
        try { body = await response.json(); } catch (e) { /* non-JSON */ }
        if (!response.ok) {
            const code = body && body.error ? body.error : ('HTTP_' + response.status);
            const message = body && body.message ? body.message : null;
            throw localError(code, message);
        }
        return body;
    }

    function localError(code, message) {
        const err = new Error(message || code);
        err.code = code;
        err.userMessage = message || null;
        return err;
    }

    function withTimeout(promise, ms) {
        let timer;
        const timeout = new Promise((_, reject) => {
            timer = setTimeout(() => reject(localError('TIMEOUT')), ms || 12000);
        });
        return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
    }

    function friendlyMessage(err) {
        if (err.userMessage) return err.userMessage;
        switch (err && err.code) {
            case 'CITY_NOT_FOUND':
                return 'We couldn\'t find that city. Check the spelling and try again.';
            case 'TIMEOUT':
                return 'The request timed out. Please try again.';
            case 'NETWORK':
                return 'Can\'t reach the weather server. Is the app running?';
            case 'VALIDATION_ERROR':
                return 'That search doesn\'t look valid. Please try a different city.';
            default:
                return 'Something went wrong while loading the weather. Please try again.';
        }
    }

    /* ------------------------------------------------------------------ theme */

    function effectiveTheme() {
        const stored = storeGet(STORE.theme, null);
        if (stored === 'dark' || stored === 'light') return stored;
        return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
            ? 'dark' : 'light';
    }

    function applyTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        const btn = $('#themeToggle');
        if (btn) btn.textContent = theme === 'dark' ? '☀️' : '🌙';
        if (btn) btn.title = theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode';
        rebuildCharts();
    }

    function cycleTheme() {
        const next = effectiveTheme() === 'dark' ? 'light' : 'dark';
        storeSet(STORE.theme, next);
        applyTheme(next);
    }

    /* ------------------------------------------------------------------ unit */

    function setUnit(unit) {
        if (state.unit === unit) return;
        state.unit = unit;
        storeSet(STORE.unit, unit);
        $('#unitC').classList.toggle('is-active', unit === 'C');
        $('#unitC').setAttribute('aria-pressed', unit === 'C');
        $('#unitF').classList.toggle('is-active', unit === 'F');
        $('#unitF').setAttribute('aria-pressed', unit === 'F');
        if (state.last) {
            // Re-render everything locally - never re-fetch the API for a display switch.
            updateHero(state.last.current);
            renderCurrent(state.last.current);
            if (state.last.forecast) {
                renderForecast(state.last.forecast, state.last.current);
            }
        }
    }

    /* ------------------------------------------------------------------ detail tiles */

    function renderCurrent(dto) {
        const grid = $('#detailGrid');
        grid.innerHTML = '';

        const tiles = [];
        tiles.push(['💧', 'Humidity', `${dto.humidityPercent}%`]);
        const compass = compassOf(dto.windDirectionDeg);
        tiles.push(['🌬️', 'Wind',
            `${windText(dto.windSpeedMps)} ${windUnit()}${compass ? ' ' + compass : ''}`]);
        tiles.push(['📊', 'Pressure', `${dto.pressureHpa} hPa`]);
        if (dto.visibilityKm !== null && dto.visibilityKm !== undefined) {
            tiles.push(['👁️', 'Visibility', `${dto.visibilityKm} km`]);
        }
        tiles.push(['☁️', 'Cloudiness', `${dto.cloudinessPercent}%`]);
        tiles.push(['🌦️', 'Precipitation (1h)', `${dto.precipitationMm.toFixed(1)} mm`]);
        if (dto.dewPointC !== null && dto.dewPointC !== undefined) {
            tiles.push(['🌫️', 'Dew point', tempText(dto.dewPointC)]);
        }
        if (dto.sunriseEpoch) {
            tiles.push(['🌅', 'Sunrise', timeText(dto.sunriseEpoch, dto.timezoneOffsetSeconds)]);
        }
        if (dto.sunsetEpoch) {
            tiles.push(['🌇', 'Sunset', timeText(dto.sunsetEpoch, dto.timezoneOffsetSeconds)]);
        }

        tiles.forEach(([icon, label, value]) => {
            grid.append(el('div', { class: 'detail' }, [
                el('span', { class: 'detail-icon', text: icon, 'aria-hidden': 'true' }),
                el('span', { class: 'detail-body' }, [
                    el('span', { class: 'detail-label', text: label }),
                    el('span', { class: 'detail-value', text: value }),
                ]),
            ]));
        });
    }

    /* ------------------------------------------------------------------ current hero */

    function updateHero(dto) {
        const label = placeLabel(dto);
        $('#heroCity').textContent = dto.city || 'Unknown location';
        $('#heroCondition').textContent = titleCase(dto.condition || dto.description || '');

        const d = localDate(dto.observationEpoch || Date.now() / 1000, dto.timezoneOffsetSeconds);
        $('#heroDate').textContent =
            `${DAYS[d.getUTCDay()]}, ${d.getUTCDate()} ${MONTHS[d.getUTCMonth()]} · local ${timeText(dto.observationEpoch || Date.now() / 1000, dto.timezoneOffsetSeconds)}`;

        const oldIcon = $('#heroIcon');
        oldIcon.parentNode.replaceChild(heroIconImg(dto.iconCode), oldIcon);

        $('#heroTemp').textContent = Math.round(toDisplayC(dto.temperatureC));
        $('#heroFeels').textContent = feelsText(dto.feelsLikeC);
        $('#heroDesc').textContent = titleCase(dto.description);

        // favourite state
        const favBtn = $('#favBtn');
        const isFav = state.favs.some((f) => f.toLowerCase() === label.toLowerCase());
        favBtn.classList.toggle('is-fav', isFav);
        favBtn.setAttribute('aria-pressed', isFav);
        favBtn.title = isFav ? 'Remove from favourites' : 'Save to favourites';

        const note = $('#updatedNote');
        note.hidden = false;
        note.textContent = `Updated ${timeText(dto.observationEpoch, dto.timezoneOffsetSeconds)} local time · data by OpenWeather`;
    }

    const placeLabel = (dto) => {
        const city = (dto.city || '').trim();
        const country = (dto.country || '').trim();
        return country ? `${city}, ${country}` : city;
    };

    /* ------------------------------------------------------------------ hourly */

    function renderHourly(forecast, current) {
        const strip = $('#hourlyStrip');
        strip.innerHTML = '';

        const nowCell = el('div', { class: 'hour-cell is-now', title: 'Current conditions' }, [
            el('span', { class: 'hour-time', text: 'Now' }),
            iconImg(current.iconCode, '', 42),
            el('span', { class: 'hour-temp', text: tempText(current.temperatureC) }),
        ]);
        strip.append(nowCell);

        (forecast.hourly || []).forEach((item) => {
            const pop = Math.round((item.precipitationProbability || 0) * 100);
            strip.append(el('div', { class: 'hour-cell' }, [
                el('span', { class: 'hour-time', text: timeText(item.epoch, forecast.timezoneOffsetSeconds) }),
                iconImg(item.iconCode, '', 42),
                el('span', { class: 'hour-temp', text: tempText(item.temperatureC) }),
                el('span', {
                    class: 'hour-pop' + (pop > 0 ? '' : ' is-dry'),
                    text: pop > 0 ? `💧${pop}%` : '—',
                }),
            ]));
        });
    }

    /* ------------------------------------------------------------------ daily */

    function renderDaily(forecast) {
        const grid = $('#dailyGrid');
        grid.innerHTML = '';
        const items = forecast.daily || [];
        $('#dailyHint').textContent = items.length
            ? `Next ${items.length} day${items.length === 1 ? '' : 's'} (3-hour data)`
            : '';

        items.forEach((day) => {
            const pop = Math.round((day.precipitationProbability || 0) * 100);
            grid.append(el('div', { class: 'daily-card' }, [
                el('div', { class: 'daily-day', text: formatEpochForToday(day.epoch, forecast.timezoneOffsetSeconds) }),
                el('div', { class: 'daily-date', text: dateText(day.epoch, forecast.timezoneOffsetSeconds) }),
                iconImg(day.iconCode, '', 52),
                el('div', { class: 'daily-desc', text: titleCase(day.description) }),
                el('div', { class: 'daily-temps' }, [
                    el('span', { class: 'daily-high', text: Math.round(toDisplayC(day.temperatureMaxC)) + '°' }),
                    el('span', { class: 'daily-low', text: Math.round(toDisplayC(day.temperatureMinC)) + '°' }),
                ]),
                el('span', {
                    class: 'daily-pop' + (pop > 0 ? '' : ' is-dry'),
                    text: pop > 0 ? `💧 ${pop}% chance` : '',
                }),
            ]));
        });
    }

    /* ------------------------------------------------------------------ charts */

    function cssVar(name) {
        const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
        return value || (name.includes('grid') ? 'rgba(128,128,128,.2)' : '#888');
    }

    const hourlyChartData = (forecast) => (forecast.hourly || []).map((item) => ({
        label: timeText(item.epoch, forecast.timezoneOffsetSeconds),
        temp: toDisplayC(item.temperatureC),
        pop: (item.precipitationProbability || 0) * 100,
        mm: item.precipitationMm,
        wind: item.windSpeedMps * windPerUnit(),
        desc: titleCase(item.description),
    }));

    function chartOptions(yTitle, unitSuffix) {
        return {
            responsive: true,
            maintainAspectRatio: false,
            animation: { duration: 400 },
            interaction: { mode: 'index', intersect: false },
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        title: (items) => items.length ? items[0].label : '',
                        label: (ctx) => ` ${ctx.dataset.label}: ${ctx.parsed.y !== null && ctx.parsed.y !== undefined ? ctx.parsed.y : '—'}${unitSuffix || ''}`,
                    },
                },
            },
            scales: {
                x: {
                    ticks: { color: cssVar('--chart-text'), maxRotation: 0, autoSkip: true, maxTicksLimit: 10 },
                    grid: { color: 'transparent' },
                },
                y: {
                    beginAtZero: false,
                    title: { display: true, text: yTitle, color: cssVar('--chart-text') },
                    ticks: { color: cssVar('--chart-text') },
                    grid: { color: cssVar('--chart-grid') },
                },
            },
        };
    }

    function ensureChart(id, config) {
        const canvas = $('#' + id);
        if (!canvas || !state.chartLib) return null;
        const existing = state.charts[id];
        if (existing) existing.destroy();
        state.charts[id] = new state.chartLib(canvas, config);
        return state.charts[id];
    }

    function rebuildCharts() {
        if (!state.chartLib) return;
        if (!state.last || !state.last.forecast) return;
        const rows = hourlyChartData(state.last.forecast);
        if (!rows.length) {
            const missing = $('#chartsMissing');
            if (missing) missing.hidden = false;
            $$('.chart-block').forEach((block) => block.classList.add('hidden'));
            return;
        }
        const missing = $('#chartsMissing');
        if (missing) missing.hidden = true;
        $$('.chart-block').forEach((block) => block.classList.remove('hidden'));

        const gridColor = cssVar('--chart-grid');
        const textColor = cssVar('--chart-text');

        // Temperature line
        const tempOpts = chartOptions(tempUnit(), '°');
        tempOpts.scales.y.beginAtZero = false;
        ensureChart('tempChart', {
            type: 'line',
            data: {
                labels: rows.map((r) => r.label),
                datasets: [{
                    label: 'Temperature',
                    data: rows.map((r) => r.temp),
                    borderColor: cssVar('--chart-line'),
                    backgroundColor: cssVar('--chart-fill'),
                    fill: true,
                    tension: 0.35,
                    pointRadius: 3,
                    pointBackgroundColor: cssVar('--chart-line'),
                }],
            },
            options: tempOpts,
        });

        // Precipitation probability bars + precipitation amount
        const precipOpts = chartOptions('Probability %', '%');
        precipOpts.scales.y.min = 0;
        precipOpts.scales.y.max = 100;
        precipOpts.scales.y2 = {
            position: 'right',
            beginAtZero: true,
            title: { display: true, text: 'mm', color: cssVar('--chart-text') },
            ticks: { color: cssVar('--chart-text') },
            grid: { drawOnChartArea: false, color: gridColor },
        };
        precipOpts.scales.x.grid = { color: 'transparent' };
        precipOpts.plugins.legend.display = false;
        ensureChart('precipChart', {
            type: 'bar',
            data: {
                labels: rows.map((r) => r.label),
                datasets: [
                    {
                        label: 'Precip. probability',
                        data: rows.map((r) => r.pop),
                        backgroundColor: cssVar('--chart-pop'),
                        borderRadius: 4,
                        yAxisID: 'y',
                    },
                    {
                        label: 'Precipitation (mm)',
                        data: rows.map((r) => r.mm),
                        backgroundColor: cssVar('--chart-wind'),
                        borderRadius: 4,
                        yAxisID: 'y2',
                    },
                ],
            },
            options: precipOpts,
        });

        // Wind speed
        const windOpts = chartOptions(windUnit(), ' ' + windUnit());
        ensureChart('windChart', {
            type: 'line',
            data: {
                labels: rows.map((r) => r.label),
                datasets: [{
                    label: 'Wind speed',
                    data: rows.map((r) => r.wind),
                    borderColor: cssVar('--chart-wind'),
                    backgroundColor: 'transparent',
                    tension: 0.35,
                    pointRadius: 3,
                    pointBackgroundColor: cssVar('--chart-wind'),
                }],
            },
            options: windOpts,
        });

        // keep grids/ticks readable for dark mode
        const fontColor = cssVar('--chart-text');
        if (state.chartLib.defaults) {
            state.chartLib.defaults.color = fontColor;
        }
    }

    /* ------------------------------------------------------------------ render forecast sections */

    function renderForecast(forecast, current) {
        renderHourly(forecast, current);
        renderDaily(forecast);
        state.last.forecast = forecast;
        rebuildCharts();
    }

    /* ------------------------------------------------------------------ loading toggles */

    function setHeroLoading(on) {
        $('#heroCity').closest('.hero-card').classList.toggle('is-loading', on);
        $('.hero-skeleton').classList.toggle('hidden', !on);
        $('#detailGrid').classList.toggle('hidden', on);
        $('#updatedNote').classList.toggle('hidden', on);
        const tempRow = $('.hero-temp-wrap');
        if (tempRow) tempRow.classList.toggle('hidden', on);
        const heroDesc = $('#heroDesc');
        if (heroDesc) heroDesc.classList.toggle('hidden', on);
    }

    function setForecastLoading(on) {
        $('#hourlyStrip').classList.toggle('hidden', on);
        $('#stripSkeleton').classList.toggle('hidden', !on);
        $('#dailyGrid').classList.toggle('hidden', on);
        $('#dailySkeleton').classList.toggle('hidden', !on);
        const chartsSkeleton = $('#chartsSkeleton');
        const chartBlocks = $$('.chart-block');
        if (chartsSkeleton) chartsSkeleton.classList.toggle('hidden', !on);
        chartBlocks.forEach((block) => block.classList.toggle('hidden', on));
        if (!on) {
            // Charts created while hidden have zero size; let Chart.js re-measure once visible.
            requestAnimationFrame(() => {
                Object.values(state.charts).forEach((chart) => chart && chart.resize());
            });
        }
    }

    function showDashboard() {
        $('#emptyState').hidden = true;
        $('#dashboard').hidden = false;
    }

    /* ------------------------------------------------------------------ favourites + recents */

    function placeListRender(listEl, emptyEl, items, onPick, onRemove) {
        listEl.innerHTML = '';
        emptyEl.hidden = items.length > 0;
        items.forEach((label) => {
            const li = el('li', {
                role: 'option',
                tabindex: '0',
                onclick: () => onPick(label),
                onkeydown: (e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        onPick(label);
                    }
                },
            }, [
                el('span', { class: 'place-name', text: label }),
                el('button', {
                    class: 'remove-btn', 'aria-label': 'Remove ' + label,
                    text: '✕', title: 'Remove',
                    onclick: (e) => {
                        e.stopPropagation();
                        onRemove(label);
                    },
                }),
            ]);
            listEl.append(li);
        });
    }

    function saveFavs() {
        storeSet(STORE.favs, state.favs);
        renderFavs();
    }

    function syncFavButton() {
        const btn = $('#favBtn');
        if (!btn || !state.last) return;
        const isFav = state.favs.some((f) => f.toLowerCase() === state.last.label.toLowerCase());
        btn.classList.toggle('is-fav', isFav);
        btn.setAttribute('aria-pressed', String(isFav));
        btn.title = isFav ? 'Remove from favourites' : 'Save to favourites';
    }

    function renderFavs() {
        placeListRender(
            $('#favList'), $('#favEmpty'), state.favs,
            (label) => loadByLabel(label),
            (label) => {
                state.favs = state.favs.filter((f) => f.toLowerCase() !== label.toLowerCase());
                saveFavs();
                toast(`Removed ${label} from favourites.`, 'info');
            });
        syncFavButton();
    }

    function toggleFav() {
        if (!state.last) return;
        const label = state.last.label;
        const index = state.favs.findIndex((f) => f.toLowerCase() === label.toLowerCase());
        if (index >= 0) {
            state.favs.splice(index, 1);
            toast(`Removed ${label} from favourites.`, 'info');
        } else {
            state.favs.unshift(label);
            toast(`Saved ${label} to favourites ⭐`, 'success');
        }
        saveFavs();
    }

    function pushRecent(label) {
        state.recents = state.recents.filter((r) => r.toLowerCase() !== label.toLowerCase());
        state.recents.unshift(label);
        state.recents = state.recents.slice(0, 8);
        storeSet(STORE.recents, state.recents);
        renderRecents();
    }

    function renderRecents() {
        placeListRender(
            $('#recentList'), $('#recentEmpty'), state.recents,
            (label) => loadByLabel(label),
            (label) => {
                state.recents = state.recents.filter((r) => r.toLowerCase() !== label.toLowerCase());
                storeSet(STORE.recents, state.recents);
                renderRecents();
            });
    }

    function clearRecents() {
        state.recents = [];
        storeSet(STORE.recents, state.recents);
        renderRecents();
        toast('Search history cleared.', 'info');
    }

    /* ------------------------------------------------------------------ loading weather */

    async function loadByLabel(label) {
        await loadWeather({ query: label, label });
    }

    function loadByCity(rawCity) {
        const city = (rawCity || '').trim();
        if (!city) {
            toast('Type a city name first.', 'error');
            shakeSearch();
            return;
        }
        loadWeather({ query: city, label: null });
    }

    async function loadByCoords(lat, lon) {
        await loadWeather({ coords: { lat, lon }, query: null, label: null });
    }

    function shakeSearch() {
        const wrap = $('.search-wrap');
        wrap.style.animation = 'none';
        void wrap.offsetWidth;
        wrap.style.animation = 'fadeUp .3s ease 2';
        setTimeout(() => { wrap.style.animation = ''; }, 700);
    }

    async function loadWeather(options) {
        const seq = ++state.loadSeq;
        showDashboard();
        setHeroLoading(true);
        setForecastLoading(true);
        setLocateBusy(true);
        $('#favBtn').disabled = true;

        try {
            const currentPromise = options.coords
                ? withTimeout(apiJSON(`/api/weather/coordinates?lat=${encodeURIComponent(options.coords.lat)}&lon=${encodeURIComponent(options.coords.lon)}`))
                : withTimeout(apiJSON('/api/weather?city=' + encodeURIComponent(options.query)));

            const current = await currentPromise;
            if (seq !== state.loadSeq) return;

            const label = options.label
                || placeLabel(current);
            state.last = { current, forecast: null, label };

            updateHero(current);
            renderCurrent(current);
            setHeroLoading(false);
            pushRecent(label);

            // forecast uses the canonical location name returned by the API
            const forecastQuery = placeLabel(current) || options.query;
            const forecast = await withTimeout(
                apiJSON('/api/weather/forecast?city=' + encodeURIComponent(forecastQuery)));
            if (seq !== state.loadSeq) return;

            state.last.forecast = forecast;
            renderForecast(forecast, current);
            setForecastLoading(false);
            if (options.coords) toast(`Showing weather for ${placeLabel(current)} 📍`, 'success');
        } catch (err) {
            if (seq !== state.loadSeq) return;
            toast(friendlyMessage(err), 'error');
            if (!state.last) {
                // first load failed - welcome panel guides the user back to search
                $('#emptyState').hidden = false;
                $('#dashboard').hidden = true;
            } else {
                setHeroLoading(false);
                setForecastLoading(false);
            }
        } finally {
            if (seq === state.loadSeq) {
                setLocateBusy(false);
                $('#favBtn').disabled = false;
            }
        }
    }

    function setLocateBusy(busy) {
        const btn = $('#locateBtn');
        btn.disabled = busy;
        btn.title = busy ? 'Loading…' : 'Use my current location';
    }

    /* ------------------------------------------------------------------ geolocation */

    function useMyLocation() {
        if (!('geolocation' in navigator)) {
            toast('Geolocation is not supported by this browser. Search for a city instead.', 'error');
            return;
        }
        toast('Locating you…', 'info', 3000);
        const ok = async (pos) => {
            const { latitude, longitude } = pos.coords;
            await loadByCoords(round(latitude, 4), round(longitude, 4));
        };
        const fail = (err) => {
            const codes = {
                1: 'Location access was denied. Allow it in your browser or search for a city instead.',
                2: 'Your position is currently unavailable. Please try again.',
                3: 'Locating you timed out. Please try again.',
            };
            toast(codes[err && err.code] || 'Could not determine your location.', 'error');
        };
        navigator.geolocation.getCurrentPosition(ok, fail, {
            enableHighAccuracy: false,
            timeout: 10000,
            maximumAge: 300000,
        });
    }

    /* ------------------------------------------------------------------ search suggestions */

    let suggestionTimer = null;
    let suggestionSeq = 0;
    let highlightedIndex = -1;
    let suggestionItems = [];

    function searchInput() {
        const query = $('#searchInput').value.trim();
        const clearBtn = $('#searchClear');
        clearBtn.hidden = query.length === 0;
        clearTimeout(suggestionTimer);
        if (query.length < 2) {
            hideSuggestions();
            return;
        }
        suggestionTimer = setTimeout(() => fetchSuggestions(query), 280);
    }

    async function fetchSuggestions(query) {
        const seq = ++suggestionSeq;
        try {
            const list = await withTimeout(
                apiJSON('/api/location/search?query=' + encodeURIComponent(query)), 8000);
            if (seq !== suggestionSeq) return;
            renderSuggestions(list);
        } catch (err) {
            if (seq !== suggestionSeq) return;
            hideSuggestions();
            // suggestions are a nicety - never crash the page on their failure
        }
    }

    function renderSuggestions(list) {
        const box = $('#suggestions');
        box.innerHTML = '';
        suggestionItems = list;
        highlightedIndex = -1;

        if (!list || !list.length) {
            const li = el('li', { class: 'is-meta', text: 'No matching cities found' });
            box.append(li);
            box.hidden = false;
            return;
        }
        list.forEach((place, i) => {
            const name = place.name || place.displayLabel;
            const detail = place.displayLabel && place.displayLabel !== name ? place.displayLabel : (place.country || '');
            const li = el('li', {
                role: 'option',
                tabindex: '-1',
                onclick: () => pickSuggestion(place),
                onmouseenter: () => {
                    highlightedIndex = i;
                    refreshHighlight();
                },
            }, [
                el('span', { class: 'sug-icon', text: '📍' }),
                el('span', { class: 'sug-name', text: name }),
                detail ? el('span', { class: 'sug-country', text: detail }) : null,
            ]);
            box.append(li);
        });
        box.hidden = false;
        $('#searchInput').setAttribute('aria-expanded', 'true');
    }

    function refreshHighlight() {
        const items = $$('#suggestions li:not(.is-meta)');
        items.forEach((li, i) => li.classList.toggle('is-highlighted', i === highlightedIndex));
    }

    function pickSuggestion(place) {
        const label = place.displayLabel || [place.name, place.country].filter(Boolean).join(', ');
        $('#searchInput').value = label.split(',')[0]; // keep input tidy; we load by full label
        hideSuggestions();
        $('#searchInput').blur();
        loadWeather({ query: label, label });
    }

    function hideSuggestions() {
        const box = $('#suggestions');
        if (box) {
            box.hidden = true;
            box.innerHTML = '';
        }
        suggestionItems = [];
        $('#searchInput').setAttribute('aria-expanded', 'false');
    }

    function onSearchKeydown(e) {
        const visible = !$('#suggestions').hidden;
        if (visible && suggestionItems.length) {
            if (e.key === 'ArrowDown') {
                e.preventDefault();
                highlightedIndex = (highlightedIndex + 1) % suggestionItems.length;
                refreshHighlight();
                return;
            }
            if (e.key === 'ArrowUp') {
                e.preventDefault();
                highlightedIndex = (highlightedIndex - 1 + suggestionItems.length) % suggestionItems.length;
                refreshHighlight();
                return;
            }
            if (e.key === 'Enter' && highlightedIndex >= 0) {
                e.preventDefault();
                pickSuggestion(suggestionItems[highlightedIndex]);
                return;
            }
        }
        if (e.key === 'Enter') {
            e.preventDefault();
            hideSuggestions();
            loadByCity($('#searchInput').value);
        }
        if (e.key === 'Escape') {
            hideSuggestions();
        }
    }

    /* ------------------------------------------------------------------ init */

    function init() {
        // theme + unit state
        state.unit = storeGet(STORE.unit, 'C') === 'F' ? 'F' : 'C';
        $('#unitC').classList.toggle('is-active', state.unit === 'C');
        $('#unitC').setAttribute('aria-pressed', String(state.unit === 'C'));
        $('#unitF').classList.toggle('is-active', state.unit === 'F');
        $('#unitF').setAttribute('aria-pressed', String(state.unit === 'F'));
        applyTheme(effectiveTheme());

        state.favs = storeGet(STORE.favs, []);
        state.recents = storeGet(STORE.recents, []);
        if (!Array.isArray(state.favs)) state.favs = [];
        if (!Array.isArray(state.recents)) state.recents = [];
        renderFavs();
        renderRecents();

        // events
        $('#themeToggle').addEventListener('click', cycleTheme);
        $('#unitC').addEventListener('click', () => setUnit('C'));
        $('#unitF').addEventListener('click', () => setUnit('F'));
        $('#locateBtn').addEventListener('click', useMyLocation);
        $('#favBtn').addEventListener('click', toggleFav);
        $('#clearRecent').addEventListener('click', clearRecents);

        const input = $('#searchInput');
        input.addEventListener('input', searchInput);
        input.addEventListener('keydown', onSearchKeydown);
        input.addEventListener('focus', () => {
            if (input.value.trim().length >= 2) fetchSuggestions(input.value.trim());
        });
        $('#searchClear').addEventListener('click', () => {
            input.value = '';
            input.focus();
            hideSuggestions();
            $('#searchClear').hidden = true;
        });
        $('#searchForm').addEventListener('submit', (e) => e.preventDefault());
        document.addEventListener('click', (e) => {
            if (!e.target.closest('.search-form')) hideSuggestions();
        });

        $('#quickChips').addEventListener('click', (e) => {
            const chip = e.target.closest('.chip');
            if (!chip) return;
            const city = chip.getAttribute('data-city');
            $('#searchInput').value = city;
            loadByCity(city);
        });

        // system-theme follow (only while the user hasn't picked explicitly)
        if (window.matchMedia) {
            window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
                if (!storeGet(STORE.theme, null)) {
                    applyTheme(e.matches ? 'dark' : 'light');
                }
            });
        }

        // "/" focuses search
        document.addEventListener('keydown', (e) => {
            const tag = (e.target && e.target.tagName) || '';
            if (e.key === '/' && !/INPUT|TEXTAREA|SELECT/.test(tag)) {
                e.preventDefault();
                input.focus();
            }
        });

        // register chart defaults once
        if (state.chartLib && state.chartLib.defaults) {
            state.chartLib.defaults.font.family = getComputedStyle(document.body).fontFamily;
            state.chartLib.defaults.font.size = 11;
        } else {
            $('#chartsMissing').hidden = false;
        }
    }

    document.addEventListener('DOMContentLoaded', init);
})();
