(function () {
  const API = window.EV_API_URL || "https://elbilsladdning.onrender.com";

  // Auto-injicera uppstartssplashen. WP-sidan är en manuell kopia och laddar denna fil;
  // så här får den ev-splash.js utan att markupen behöver klistras om (samma mönster som
  // CarAdvice). Hoppar över om skriptet redan finns i sidan.
  (function injectSplash() {
    if (document.getElementById("ev-splash-js") ||
        document.querySelector('script[src*="ev-splash.js"]')) return;
    var s = document.createElement("script");
    s.id = "ev-splash-js";
    s.src = API + "/ev-splash.js";
    s.defer = true;
    (document.head || document.documentElement).appendChild(s);
  })();
  let state = { lat: null, lon: null, city: "", sort: "speed", carIndex: null, cars: [], filter: "all", operatorFilter: null, lastData: null, lastRoute: null, lastCalc: null, favorites: [], evSalesRank: [] };
  let evMap = null;
  let evMapMarkers = [];
  let evRoutePolyline = null;
  let savedStationsHtml = null;

  (function injectStyles() {
    const s = document.createElement("style");
    s.textContent =
      ".ev-op-chip{padding:4px 11px;border-radius:20px;border:1.5px solid rgba(59,130,246,0.2);background:rgba(59,130,246,0.06);color:rgba(147,197,253,0.7);font-size:.72rem;font-weight:600;cursor:pointer;transition:all .15s;white-space:nowrap;}" +
      ".ev-op-chip:hover{border-color:rgba(59,130,246,0.5);color:#93c5fd;background:rgba(59,130,246,0.12);}" +
      ".ev-op-chip.ev-op-active{border-color:rgba(59,130,246,0.6);color:#fff;background:linear-gradient(135deg,#1d4ed8,#2563eb);}" +
      ".ev-route-panel{background:#0d1526;border:1px solid rgba(59,130,246,0.2);border-radius:14px;padding:20px;margin-bottom:16px;}" +
      ".ev-route-title{font-size:.72rem;font-weight:700;text-transform:uppercase;letter-spacing:.1em;color:rgba(147,197,253,.7);margin-bottom:14px;}" +
      ".ev-route-row{display:flex;gap:8px;align-items:center;margin-bottom:10px;}" +
      ".ev-route-input{flex:1;padding:10px 13px;background:#060c1a;border:1.5px solid rgba(59,130,246,.2);border-radius:9px;color:#f0f4ff;font-size:.88rem;}" +
      ".ev-route-input:focus{outline:none;border-color:#3b82f6;}" +
      ".ev-route-input::placeholder{color:rgba(200,215,255,.35);}" +
      ".ev-route-btn{padding:10px 18px;background:linear-gradient(135deg,#1d4ed8,#2563eb);border:none;border-radius:9px;color:#fff;font-size:.82rem;font-weight:700;cursor:pointer;white-space:nowrap;}" +
      ".ev-route-btn:hover{opacity:.88;}" +
      ".ev-route-btn:disabled{opacity:.45;cursor:default;}" +
      ".ev-route-timeline{margin-top:14px;}" +
      ".ev-route-stop{display:flex;gap:12px;align-items:flex-start;margin-bottom:12px;}" +
      ".ev-route-dot{width:28px;height:28px;border-radius:50%;background:linear-gradient(135deg,#1d4ed8,#2563eb);border:2px solid #3b82f6;display:flex;align-items:center;justify-content:center;font-size:.7rem;font-weight:800;color:#fff;flex-shrink:0;margin-top:2px;}" +
      ".ev-route-dot.start{background:linear-gradient(135deg,#16a34a,#22c55e);border-color:#22c55e;}" +
      ".ev-route-dot.end{background:linear-gradient(135deg,#dc2626,#ef4444);border-color:#ef4444;}" +
      ".ev-route-line{width:2px;background:rgba(59,130,246,.25);margin:0 13px;flex-shrink:0;align-self:stretch;min-height:12px;}" +
      ".ev-route-info{flex:1;min-width:0;}" +
      ".ev-route-name{font-size:.88rem;font-weight:700;color:#f0f4ff;margin-bottom:2px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}" +
      ".ev-route-meta{font-size:.75rem;color:rgba(200,215,255,.55);}" +
      "@media(max-width:500px){" +
      ".ev-station-body{padding:10px 10px!important;gap:0 8px!important;grid-template-columns:auto 1fr!important;}" +
      ".ev-rank{grid-row:1/4!important;width:24px!important;height:24px!important;font-size:.68rem!important;}" +
      ".ev-station-name{white-space:normal!important;font-size:.9rem!important;line-height:1.3!important;}" +
      ".ev-station-addr{white-space:normal!important;font-size:.73rem!important;line-height:1.3!important;}" +
      ".ev-station-tags{gap:4px!important;margin-top:5px!important;}" +
      ".ev-station-right{grid-column:2!important;grid-row:3!important;flex-direction:row!important;align-items:center!important;justify-content:flex-start!important;gap:8px!important;flex-wrap:wrap!important;margin-top:4px!important;}" +
      ".ev-dist{font-size:.73rem!important;}" +
      ".ev-price-badge{font-size:.72rem!important;padding:2px 7px!important;}" +
      ".ev-fav-btn{margin-top:0!important;}" +
    "}" +
    ".ev-section-divider{display:flex;align-items:center;gap:10px;margin:20px 0 4px;}" +
    ".ev-divider-line{flex:1;height:1px;background:linear-gradient(90deg,transparent,rgba(251,191,36,0.4),transparent);}" +
    ".ev-divider-badge{background:rgba(251,191,36,0.1);border:1px solid rgba(251,191,36,0.3);border-radius:20px;padding:3px 14px;font-size:11px;font-weight:700;color:#fbbf24;letter-spacing:0.06em;white-space:nowrap;}" +
    ".ev-fact-nav{background:rgba(59,130,246,0.08);border:1.5px solid rgba(59,130,246,0.2);border-radius:8px;color:rgba(147,197,253,0.7);font-size:20px;width:32px;height:32px;cursor:pointer;display:flex;align-items:center;justify-content:center;transition:all .15s;padding:0;line-height:1;}" +
    ".ev-fact-nav:hover{background:rgba(59,130,246,0.2);color:#93c5fd;}" +
    ".ev-fact-dot{width:8px;height:8px;border-radius:50%;border:none;background:rgba(147,197,253,0.2);cursor:pointer;padding:0;transition:all .25s;flex-shrink:0;}" +
    ".ev-fact-dot.ev-fact-dot-active{background:#3b82f6;width:20px;border-radius:4px;}" +
    "@keyframes ev-slide-in{from{opacity:0;transform:translateY(8px)}to{opacity:1;transform:none}}" +
    "@keyframes ev-slide-out{from{opacity:1;transform:none}to{opacity:0;transform:translateY(-8px)}}" +
    ".ev-slide-entering{animation:ev-slide-in .5s cubic-bezier(.22,1,.36,1) forwards;}" +
    ".ev-slide-leaving{animation:ev-slide-out .35s ease forwards;pointer-events:none;position:absolute;top:0;left:0;width:100%;}";
    document.head.appendChild(s);
  })();

  function renderMap(userLat, userLon, stations) {
    if (!window.L) return;
    const mapEl = document.getElementById("ev-map");
    if (!mapEl) return;
    mapEl.style.display = "block";

    if (evRoutePolyline) { evRoutePolyline.remove(); evRoutePolyline = null; }

    if (!evMap) {
      evMap = L.map("ev-map", { zoomControl: true }).setView([userLat, userLon], 17);
      L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
        attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
        maxZoom: 19
      }).addTo(evMap);
      evMap.invalidateSize();
    } else {
      evMapMarkers.forEach(m => m.remove());
      evMapMarkers = [];
      evMap.setView([userLat, userLon], 17);
    }

    const userIcon = L.divIcon({
      className: "",
      html: '<div style="width:18px;height:18px;border-radius:50%;background:#3b82f6;border:3px solid #fff;box-shadow:0 0 0 3px rgba(59,130,246,0.35),0 2px 8px rgba(0,0,0,0.4)"></div>',
      iconSize: [18, 18], iconAnchor: [9, 9]
    });
    evMapMarkers.push(L.marker([userLat, userLon], { icon: userIcon }).addTo(evMap).bindPopup("📍 Din position"));

    stations.forEach((s, i) => {
      if (!s.lat || !s.lon) return;
      const color = s.maxEffKw >= 100 ? "#22c55e" : s.maxEffKw >= 22 ? "#f59e0b" : "#818cf8";
      const icon = L.divIcon({
        className: "",
        html: `<div style="width:30px;height:30px;border-radius:50%;background:${color};border:2.5px solid #fff;display:flex;align-items:center;justify-content:center;font-size:11px;font-weight:800;color:#111;box-shadow:0 2px 8px rgba(0,0,0,0.45)">${i + 1}</div>`,
        iconSize: [30, 30], iconAnchor: [15, 15], popupAnchor: [0, -15]
      });
      const price = s.chargepricePerKwh || s.usageCost || "okänt pris";
      const popup = `<b>${s.name}</b><br>⚡ ${Math.round(s.maxEffKw)} kW · ${s.connectorType}<br>📍 ${s.distanceKm.toFixed(1)} km bort<br>💰 ${price}`;
      evMapMarkers.push(L.marker([s.lat, s.lon], { icon }).addTo(evMap).bindPopup(popup));
    });
  }

  function getUserId() {
    let uid = localStorage.getItem("ev-user-id");
    if (!uid) {
      uid = crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).slice(2) + Date.now().toString(36);
      localStorage.setItem("ev-user-id", uid);
    }
    return uid;
  }

  async function loadFavorites() {
    try {
      const r = await fetch(`${API}/api/favorites/${getUserId()}`);
      state.favorites = r.ok ? await r.json() : [];
    } catch(_) { state.favorites = []; }
  }

  async function toggleFavorite(station) {
    const existing = state.favorites.find(f => Math.abs(f.lat - station.lat) < 0.0005);
    if (existing) {
      await fetch(`${API}/api/favorites/${existing.id}?userId=${getUserId()}`, { method: "DELETE" });
      state.favorites = state.favorites.filter(f => f.id !== existing.id);
    } else {
      const resp = await fetch(`${API}/api/favorites`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          userId: getUserId(), name: station.name, address: station.address,
          lat: station.lat, lon: station.lon, maxEffKw: station.maxEffKw,
          connectorType: station.connectorType, operator: station.operator
        })
      });
      if (resp.ok) { const saved = await resp.json(); if (saved) state.favorites.push(saved); }
    }
    if (state.lastData) renderResults(state.lastData);
  }

  loadFavorites();

  fetch(API + "/api/cars")
    .then(r => r.json())
    .then(cars => {
      state.cars = cars;
      const sel = document.getElementById("ev-car-select");
      cars.forEach((c, i) => {
        const o = document.createElement("option");
        const bat = c.batteryKwh ? (Number.isInteger(c.batteryKwh) ? c.batteryKwh : c.batteryKwh.toFixed(1)) + ' kWh · ' : '';
        o.value = i; o.textContent = `${c.name}  (${bat}AC ${c.maxAcKw} kW · DC ${c.maxDcKw} kW)`;
        sel.appendChild(o);
      });
    })
    .catch(() => {
      document.getElementById("ev-car-select").innerHTML = "<option>Kunde inte hämta bilar</option>";
    });

  fetch(API + "/api/ev-sales-rank")
    .then(r => r.json())
    .then(rows => { if (Array.isArray(rows)) state.evSalesRank = rows; })
    .catch(() => {});

  document.getElementById("ev-car-select").addEventListener("change", function () {
    const idx = parseInt(this.value);
    state.carIndex = isNaN(idx) ? null : idx;
    renderSpecs();
    if (state.lat !== null && state.carIndex !== null) fetchAndRender();
  });

  function renderSpecs() {
    const box = document.getElementById("ev-specs");
    if (state.carIndex === null) { box.style.display = "none"; return; }
    const c = state.cars[state.carIndex];
    const rangeMil  = c.rangeKm ? Math.round(c.rangeKm / 10) : null;
    const realMil   = rangeMil ? Math.round(rangeMil * 0.85) : null;
    const freqBadge = chargingFreqBadge(rangeMil);
    const priceStr  = c.priceKr ? `från ${(c.priceKr / 1000).toFixed(0)} tkr` : null;
    box.style.display = "flex";
    box.innerHTML = `
      <span class="ev-spec-badge badge-ac">AC max ${c.maxAcKw} kW</span>
      <span class="ev-spec-badge badge-dc">DC max ${c.maxDcKw} kW</span>
      ${rangeMil ? `<span class="ev-spec-badge badge-range">~${rangeMil} mil WLTP · ~${realMil} mil verklig</span>` : ""}
      ${priceStr ? `<span class="ev-spec-badge badge-price">${priceStr}</span>` : ""}
      ${freqBadge ? `<span class="ev-spec-badge badge-freq">${freqBadge}</span>` : ""}
      ${c.connectors.map(t => `<span class="ev-spec-badge badge-con">${conLabel(t)}</span>`).join("")}`;
  }

  function conLabel(t) { return { type2:"Type 2", ccs:"CCS", chademo:"CHAdeMO" }[t] || t; }

  document.getElementById("ev-daily-mil").addEventListener("input", renderSpecs);

  function chargingFreqBadge(rangeMil) {
    const annualMil = parseFloat(document.getElementById("ev-daily-mil").value);
    if (!annualMil || isNaN(annualMil) || !rangeMil) return null;
    const dailyMil = annualMil / 365;
    const usableMil = rangeMil * 0.85 * 0.60;
    const days = usableMil / dailyMil;
    let label;
    if (days < 1)        label = `${Math.ceil(7 / days)} ggr/vecka`;
    else if (days < 1.5) label = "ladda varje dag";
    else if (days < 2.5) label = "ladda varannan dag";
    else                 label = `ladda var ${Math.round(days)}:e dag`;
    return `🔋 ${label}`;
  }

  document.querySelectorAll("[data-sort]").forEach(btn => {
    btn.addEventListener("click", () => {
      document.querySelectorAll("[data-sort]").forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      state.sort = btn.dataset.sort;
      if (state.lat !== null && state.carIndex !== null) fetchAndRender();
    });
  });

  document.querySelectorAll(".ev-filter-btn").forEach(btn => {
    btn.addEventListener("click", () => {
      document.querySelectorAll(".ev-filter-btn").forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      state.filter = btn.dataset.filter;
      if (state.lastData) renderResults(state.lastData);
    });
  });

  function setLoc(type, text) {
    document.getElementById("ev-loc-dot").className = "ev-location-dot " + type;
    document.getElementById("ev-loc-text").textContent = text;
  }

  if (!navigator.geolocation) {
    setLoc("error", "GPS stöds inte i denna webbläsare");
    setOutput('<div class="ev-status">GPS ej tillgänglig.</div>');
  } else {
    navigator.geolocation.getCurrentPosition(
      async pos => {
        state.lat = pos.coords.latitude; state.lon = pos.coords.longitude;
        setLoc("", "Position hittad");
        reverseGeocode(state.lat, state.lon);
        if (state.carIndex !== null) fetchAndRender();
        else setOutput('<div class="ev-status">Välj din bilmodell ovan för att se laddstationer.</div>');
      },
      () => {
        setLoc("error", "Kunde inte hämta GPS-position");
        setOutput('<div class="ev-status">Tillåt platsåtkomst i webbläsaren och ladda om sidan.</div>');
      },
      { enableHighAccuracy: true, timeout: 10000 }
    );
  }

  async function reverseGeocode(lat, lon) {
    try {
      const r = await fetch(`https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lon}&format=json`,
        { headers: { "User-Agent": "EV-Laddning-App/1.0" } });
      const d = await r.json();
      const city = d.address?.city || d.address?.town || d.address?.village || d.address?.municipality || "";
      if (city) { state.city = city; setLoc("", city); }
    } catch (_) {}
  }

  async function fetchAndRender() {
    state.operatorFilter = null;
    setOutput('<div class="ev-status"><div class="ev-spinner"></div>Söker laddstationer och frågar AI…</div>');
    try {
      const cityParam = state.city ? `&city=${encodeURIComponent(state.city)}` : "";
      const url = `${API}/api/stations?lat=${state.lat}&lon=${state.lon}&carIndex=${state.carIndex}&sort=${state.sort}${cityParam}`;
      const resp = await fetch(url);
      if (!resp.ok) throw new Error("HTTP " + resp.status);
      renderResults(await resp.json());
    } catch (e) {
      setOutput(`<div class="ev-status">Fel: ${e.message}</div>`);
    }
  }

  const OPERATOR_URLS = {
    recharge:   "https://recharge.com/se/priser",
    ionity:     "https://ionity.eu/sv/tariffs.html",
    incharge:   "https://incharge.vattenfall.se/priser",
    circlek:    "https://www.circlek.se/elbil/laddning",
    tesla:      "https://www.tesla.com/sv_SE/supercharger",
    bee:        "https://bee-charging.com",
    eon:        "https://www.eon.se/elbil",
    clever:     "https://www.clever.dk/priser",
    mer:        "https://mer.eco/se/",
    allego:     "https://www.allego.eu/sv/",
    eviny:      "https://www.eviny.no/en/ev-charging/",
    st1:        "https://www.st1.se/tanka/el",
  };

  function operatorUrl(name) {
    if (!name) return null;
    const l = name.toLowerCase();
    if (l.includes("recharge"))                              return OPERATOR_URLS.recharge;
    if (l.includes("ionity"))                                return OPERATOR_URLS.ionity;
    if (l.includes("incharge") || l.includes("vattenfall")) return OPERATOR_URLS.incharge;
    if (l.includes("circle k") || l.includes("circlek"))    return OPERATOR_URLS.circlek;
    if (l.includes("tesla"))                                 return OPERATOR_URLS.tesla;
    if (l.includes("bee"))                                   return OPERATOR_URLS.bee;
    if (l.includes("e.on") || l.includes("eon"))             return OPERATOR_URLS.eon;
    if (l.includes("clever"))                                return OPERATOR_URLS.clever;
    if (l.includes("mer"))                                   return OPERATOR_URLS.mer;
    if (l.includes("allego"))                                return OPERATOR_URLS.allego;
    if (l.includes("eviny"))                                 return OPERATOR_URLS.eviny;
    if (l.includes("st1"))                                   return OPERATOR_URLS.st1;
    return null;
  }

  function renderResults(data) {
    state.lastData = data;
    const { carName, stations, recommendation, carFact } = data;

    const uniqueOps = [...new Set(
      stations
        .filter(s => s.operator && !s.operator.includes("Unknown") && s.operator.trim() !== "")
        .map(s => s.operator)
    )].sort();

    let visible = state.filter === "fast"
        ? stations.filter(s => s.connectorType.includes("DC") && s.maxEffKw >= 50)
        : stations;
    if (state.operatorFilter) visible = visible.filter(s => s.operator === state.operatorFilter);
    const top = visible.slice(0, 10);
    let html = "";
    let stationsHtml = "";
    let factHtml = "";

    const fastDC = stations
      .filter(s => s.maxEffKw >= 50 && s.connectorType.includes("DC"))
      .sort((a, b) => a.distanceKm - b.distanceKm)[0];

    if (fastDC) {
      const url  = operatorUrl(fastDC.operator);
      const dist = fastDC.distanceKm.toFixed(1);
      const kw   = Math.round(fastDC.maxEffKw);
      const nm   = fastDC.name.length > 42 ? fastDC.name.slice(0,40)+"…" : fastDC.name;
      const pris = fastDC.chargepricePerKwh || "";
      stationsHtml += `
        <div class="ev-fast-highlight">
          <div class="ev-fast-highlight-icon">⚡</div>
          <div class="ev-fast-highlight-body">
            <div class="ev-fast-highlight-label">Närmaste snabbladdare</div>
            <div class="ev-fast-highlight-title">${nm} · ${kw} kW</div>
            <div class="ev-fast-highlight-sub">${dist} km bort · ${fastDC.connectorType}${pris ? " · " + pris : ""}</div>
          </div>
          ${url ? `<a class="ev-fast-highlight-link" href="${url}" target="_blank" rel="noopener">Se priser →</a>` : ""}
        </div>`;
    }

    if (state.sort === "price") {
      const bat = state.carIndex !== null ? state.cars[state.carIndex]?.batteryKwh : null;
      const homeMin = bat ? Math.round(bat * 1.5) : null;
      const homeMax = bat ? Math.round(bat * 3.5) : null;
      const homeCostStr = homeMin ? ` · 🔋 Full laddning ~${homeMin}–${homeMax} kr` : "";
      html += `
        <div class="ev-home-tip">
          <div class="ev-home-tip-icon">🏠</div>
          <div><strong>Billigast: hemmaladdning</strong>
          <span> · ~1,50–3,50 kr/kWh beroende på elavtal${homeCostStr}. Ladda hemma om du kan!</span></div>
        </div>`;
    }

    if (recommendation) {
      html += `
        <div class="ev-ai-card">
          <div class="ev-ai-icon">🤖</div>
          <div style="flex:1">
            <div class="ev-ai-label" style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;">
              AI-rekommendation · ${carName}
              <span style="font-size:10px;font-weight:700;letter-spacing:0.6px;background:#f55036;color:#fff;border-radius:4px;padding:2px 7px;white-space:nowrap;">⚡ GROQ</span>
            </div>
            <div class="ev-ai-text">${recommendation}</div>
          </div>
        </div>`;
    }

    {
      const staticFacts = [
        { icon: '💰', text: 'Fyndläge på begagnad premium-el: VW-koncernen skär ner och enligt rapporter är flera tyska fabriker hotade – bland dem Neckarsulm, där Audi e-tron GT byggs. Samtidigt har första generationens <strong>Audi e-tron</strong> (2019–2021) tappat <strong>65–70 %</strong> av nypriset: median ~<strong>325 000 kr</strong> på Blocket i juli 2026, billigaste exemplaren från ~210 000 kr. Haken: 1,63 kWh/mil är törstigt även för en stor SUV, och batterigarantin (8 år/160 000 km) börjar ta slut på 2019-bilarna.' },
        { icon: '🏆', text: 'Volvo EX40 var Sveriges mest sålda elbil 2025 med <strong>8 788</strong> nyregistreringar – och EX40/XC40 leder även första halvåret 2026, före Tesla Model Y (Mobility Sweden).' },
        { icon: '🌍', text: 'IONITY är Europas snabbaste offentliga laddnätverk med upp till <strong>350 kW</strong> per laddpunkt. I Norden finns 100+ stationer längs motorvägarna.' },
        { icon: '🇸🇪', text: 'Vattenfall InCharge är ett av Nordens största laddnätverk med över <strong>33 000 laddpunkter</strong> i Sverige, Norge, Danmark och Finland.' },
        { icon: '❄️', text: 'På kall vinterdag kan räckvidden minska med <strong>20–40%</strong> jämfört med WLTP. Värm bilen medan den laddar för att spara batterienergi.' },
        { icon: '⚡', text: 'Tesla öppnade sitt Supercharger-nätverk för andra elbilsmärken i Sverige <strong>2023</strong>. CCS (Combo) är Europas dominerande DC-laddstandard.' },
        { icon: '📈', text: 'Elbilarna går framåt: <strong>45%</strong> av Sveriges nyregistreringar 2026 väntas bli elbilar enligt Mobility Swedens prognos (justerad juli 2026) – upp från 36,5% helåret 2025.' },
        { icon: '🚗', text: 'Mercedes eldrivna CLA utsågs till <strong>Årets Bil 2026</strong> (europeiska Car of the Year) – före Škoda Elroq och Kia EV4. 2025 vann Renault 5 E-Tech.' },
        { icon: '📊', text: 'Volvo dominerar den svenska nybilsmarknaden med <strong>16,5%</strong> marknadsandel i juni 2026, före Volkswagen (13,2%) och Kia (7,4%). Tesla ligger åtta med 4,4% (Mobility Sweden).' },
        { icon: '🥇', text: 'I maj 2026 var Volkswagen ID.4 Sveriges mest sålda renodlade elbil med <strong>687</strong> nyregistreringar – tätt följt av Tesla Model Y (683) och Polestar 2 (526) (Carla.se elbilsindex).' },
        { icon: '🔌', text: 'Kia EV6 (339), Volkswagen ID.3 (285) och Škoda Enyaq (276) rundade av majitoppen bland Sveriges mest sålda elbilar 2026 – före Nissan Leaf (260) och Volvo EX40 (205) (Carla.se elbilsindex).' },
        { icon: '🚀', text: 'Elbilar gick om laddhybrider i försäljning kring årsskiftet 2025/2026 och har inte tittat tillbaka – i april 2026 passerade elbilsförsäljningen till och med diesel och närmade sig bensin, fortfarande den största kategorin (Carla.se).' },
        // Nedan: hämtade ur CarAdvice-insikterna (samma skrapade motorpress som bilkortens
        // "Vad experterna säger"). Bara laddningsrelevanta rader om bilar som går att köpa —
        // kommande modeller hör hemma i CarAdvice kommande-kö, inte i en publik faktakarusell.
        { icon: '⏱️', text: 'Låg förbrukning slår stort batteri på långresa: <strong>Mercedes CLA 250+</strong> drar bara <strong>16,5 kWh/100 km</strong> och klarar därför 80 mil med ett enda laddstopp på <strong>14 minuter</strong> (Vi Bilägare).' },
        { icon: '🔋', text: '<strong>Volvo EX30</strong> tar sig 80 mil på totalt <strong>59 minuters</strong> laddning och håller sig under 20 kWh/100 km – ett bra riktvärde för hur mycket laddtid en långresa faktiskt kostar (Vi Bilägare).' },
        { icon: '🏠', text: 'Nya elbilar blir reservkraft: <strong>Volkswagen ID. Cross</strong> har både <strong>V2L</strong> (upp till 3,6 kW till externa prylar) och <strong>V2H</strong> som standard – bilen kan alltså mata ström tillbaka till hemmet (Teknikens Värld / Vi Bilägare).' },
        { icon: '⚡', text: '800 volt sprider sig nedåt i prisklasserna: <strong>BYD Atto 3 Evo</strong> fick större batteri och 800 V-laddning, och maxeffekten steg från <strong>88 kW till 220 kW</strong> (Elbilen).' },
        { icon: '🛞', text: 'Praktiskt knep i backarna: <strong>B-läget</strong> motorbromsar och laddar batteriet i stället för att elda upp farten i bromsarna. I Volvo XC40 uppges det ge <strong>en till två mils</strong> extra räckvidd – och mindre slitage på bromsbeläggen (CarUp).' },
        { icon: '🛣️', text: 'Längst på en laddning: <strong>Mercedes EQS 450+</strong> klarar <strong>925 km</strong> enligt WLTP – men räkna med mindre i verklig fart och kyla, WLTP mäts i betydligt snällare förhållanden (CarUp).' },
      ];
      const dynamicRankFacts = [];
      if (state.evSalesRank && state.evSalesRank.length > 0) {
        const top = state.evSalesRank[0];
        const second = state.evSalesRank[1];
        const period = top.periodLabel ? ' ' + top.periodLabel : '';
        const rankText =
          `${top.model} var Sveriges mest sålda elbil${period} med <strong>${top.units.toLocaleString('sv-SE')}</strong> nyregistreringar` +
          (second ? `, före ${second.model} (${second.units.toLocaleString('sv-SE')})` : '') +
          ' (elbilsvaruhuset.se / Mobility Sweden).';
        dynamicRankFacts.push({ icon: '🔋', text: rankText });
      }
      const facts = [
        ...(data.funFact ? [{ icon: '💡', text: data.funFact }] : []),
        ...dynamicRankFacts,
        ...staticFacts
      ];

      const fSlideHtml = facts.map((f, i) =>
        `<div class="ev-funfact-slide" style="display:${i===0?'flex':'none'};align-items:flex-start;gap:10px;">
          <div class="ev-funfact-icon">${f.icon}</div>
          <div>
            <div class="ev-funfact-label">Visste du att</div>
            <div class="ev-funfact-text">${f.text}</div>
          </div>
        </div>`
      ).join('');

      const fDotHtml = facts.map((_, i) =>
        `<button class="ev-fact-dot${i===0?' ev-fact-dot-active':''}" aria-label="Fakta ${i+1}"></button>`
      ).join('');

      html += `
        <div class="ev-funfact-card" id="ev-funfact-carousel" style="flex-direction:column;gap:0;">
          <div id="ev-funfact-slides" style="position:relative;min-height:48px;">${fSlideHtml}</div>
          <div style="display:flex;justify-content:center;gap:6px;margin-top:10px;">${fDotHtml}</div>
        </div>`;
    }

    if (state.carIndex !== null && state.cars.length > 0) {
      const selectedName = state.cars[state.carIndex]?.name;
      const allValid = state.cars.filter(c => c.priceKr > 0 && c.rangeKm > 0);

      const modes = [
        {
          icon: '📊', label: 'Räckvidd per 100 000 kr', colHeader: 'km/100 tkr',
          data: allValid.map(c => ({ name: c.name, val: Math.round(c.rangeKm * 100000 / c.priceKr), price: c.priceKr })).sort((a, b) => b.val - a.val),
          formatVal: v => `${v} km`,
          factFn: (best) => carFact || `Bäst värde för pengarna: ${best.name} med ${best.val} km per 100 000 kr.`
        },
        {
          icon: '⚡', label: 'Snabbast DC-laddning', colHeader: 'DC max',
          data: state.cars.filter(c => c.maxDcKw > 0 && c.priceKr > 0).map(c => ({ name: c.name, val: c.maxDcKw, price: c.priceKr })).sort((a, b) => b.val - a.val),
          formatVal: v => `${v} kW`,
          factFn: (best) => `Snabbaste DC-laddning: ${best.name} med ${best.val} kW – kostar ${(best.price / 1000).toFixed(0)} 000 kr.`
        },
        {
          icon: '🛣️', label: 'Längst räckvidd (WLTP)', colHeader: 'Räckvidd',
          data: allValid.map(c => ({ name: c.name, val: c.rangeKm, price: c.priceKr })).sort((a, b) => b.val - a.val),
          formatVal: v => `${v} km`,
          factFn: (best) => `Längst räckvidd: ${best.name} med ${best.val} km WLTP – kostar ${(best.price / 1000).toFixed(0)} 000 kr.`
        },
        {
          icon: '🎯', label: 'WLTP vs verklig räckvidd', colHeader: 'Tappar',
          data: allValid.map(c => {
            const real = Math.round(c.rangeKm * 0.85);
            return { name: c.name, val: c.rangeKm - real, price: c.priceKr, wltp: c.rangeKm, real };
          }).sort((a, b) => b.val - a.val),
          formatVal: v => `-${v} km`,
          factFn: (best) => `Störst skillnad: ${best.name} – WLTP ${best.wltp} km men ~${best.real} km verklig räckvidd (tappar ~${best.val} km). Alla bilar beräknas med 85% av WLTP som tumregel.`
        }
      ];

      const buildRow = (c, rank, hl, stripe, isWltp, formatVal) => {
        const rowBg  = hl ? 'background:rgba(59,130,246,0.12);border-left:3px solid #3b82f6;'
                         : stripe ? 'background:#dbeafe;' : 'background:#ffffff;';
        const bold   = hl ? 'font-weight:700;' : '';
        const td     = `padding:6px 10px;color:#111827;${bold}`;
        return isWltp
          ? `<tr style="${rowBg}">
              <td style="${td}color:#9ca3af;font-size:12px;">${rank}</td>
              <td style="${td}">${c.name}</td>
              <td style="${td}text-align:right;color:#374151;">${c.wltp} km</td>
              <td style="${td}text-align:right;color:#16a34a;">~${c.real} km</td>
            </tr>`
          : `<tr style="${rowBg}">
              <td style="${td}color:#9ca3af;font-size:12px;">${rank}</td>
              <td style="${td}">${c.name}</td>
              <td style="${td}text-align:right;color:#1d4ed8;">${formatVal(c.val)}</td>
              <td style="${td}text-align:right;color:#374151;">${(c.price / 1000).toFixed(0)} tkr</td>
            </tr>`;
      };

      const slides = modes.map((mode, mi) => {
        const { icon, label, colHeader, data, formatVal, factFn } = mode;
        if (!data.length) return '';
        const factText = factFn(data[0]);
        const isWltp = icon === '🎯';
        const th1 = isWltp ? 'WLTP' : colHeader;
        const th2 = isWltp ? '~Verklig' : 'Pris';
        const rows = data.map((c, i) => buildRow(c, i + 1, c.name === selectedName, i % 2 === 1, isWltp, formatVal)).join('');
        return `<div class="ev-fact-slide" data-slide="${mi}" style="display:${mi===0?'flex':'none'};gap:12px;align-items:flex-start;">
          <div class="ev-funfact-icon">${icon}</div>
          <div style="flex:1">
            <div class="ev-funfact-label">${label}</div>
            <div class="ev-funfact-text" style="margin-bottom:10px;">${factText}</div>
            <div style="border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;margin-top:4px;">
              <div style="overflow-y:auto;max-height:220px;">
                <table style="width:100%;border-collapse:collapse;font-size:13px;">
                  <thead style="position:sticky;top:0;z-index:1;"><tr style="background:#f3f4f6;border-bottom:1px solid #e5e7eb;">
                    <th style="padding:6px 10px;text-align:left;color:#9ca3af;font-weight:600;font-size:11px;text-transform:uppercase;letter-spacing:0.5px;width:24px;">#</th>
                    <th style="padding:6px 10px;text-align:left;color:#9ca3af;font-weight:600;font-size:11px;text-transform:uppercase;letter-spacing:0.5px;">Bil</th>
                    <th style="padding:6px 10px;text-align:right;color:#9ca3af;font-weight:600;font-size:11px;text-transform:uppercase;letter-spacing:0.5px;">${th1}</th>
                    <th style="padding:6px 10px;text-align:right;color:#9ca3af;font-weight:600;font-size:11px;text-transform:uppercase;letter-spacing:0.5px;">${th2}</th>
                  </tr></thead>
                  <tbody>${rows}</tbody>
                </table>
              </div>
            </div>
          </div>
        </div>`;
      }).filter(Boolean).join('');

      const dots = modes.map((_, i) =>
        `<button class="ev-fact-dot${i===0?' ev-fact-dot-active':''}" data-dot="${i}" aria-label="Fakta ${i+1}"></button>`
      ).join('');

      factHtml += `
        <div class="ev-section-divider">
          <div class="ev-divider-line"></div>
          <div class="ev-divider-badge">💡 Visste du att?</div>
          <div class="ev-divider-line"></div>
        </div>
        <div class="ev-funfact-card" id="ev-fact-carousel" style="flex-direction:column;gap:0;">
          <div id="ev-fact-slides" style="position:relative;">${slides}</div>
          <div style="display:flex;align-items:center;justify-content:space-between;margin-top:12px;">
            <button class="ev-fact-nav" id="ev-fact-prev">‹</button>
            <div style="display:flex;gap:6px;">${dots}</div>
            <button class="ev-fact-nav" id="ev-fact-next">›</button>
          </div>
        </div>`;
    }

    if (state.favorites.length > 0) {
      html += `<div class="ev-favorites-section">
        <div class="ev-favorites-title">♥ Mina favoriter (${state.favorites.length})</div>
        ${state.favorites.map(f => `
          <div class="ev-fav-item">
            <span class="ev-fav-item-name">${f.name}</span>
            <span class="ev-fav-item-meta">⚡ ${Math.round(f.maxEffKw)} kW · ${f.connectorType}</span>
            <button class="ev-fav-remove" data-id="${f.id}" title="Ta bort">✕</button>
          </div>`).join("")}
      </div>`;
    }

    if (uniqueOps.length > 1) {
      const chipAll = !state.operatorFilter ? ' ev-op-active' : '';
      stationsHtml += `<div class="ev-op-chips" style="display:flex;gap:6px;flex-wrap:wrap;margin-bottom:10px;">
        <button class="ev-op-chip${chipAll}" data-op="">Alla</button>
        ${uniqueOps.map(op => {
          const act = state.operatorFilter === op ? ' ev-op-active' : '';
          return `<button class="ev-op-chip${act}" data-op="${op}">${op}</button>`;
        }).join('')}
      </div>`;
    }

    const filterNote = state.filter === "fast" ? " · DC ≥50 kW" : "";
    const opNote = state.operatorFilter ? ` · ${state.operatorFilter}` : "";
    stationsHtml += `
      <div class="ev-results-header">
        <strong>${stations.length} kompatibla stationer inom 15 km</strong>
        <span>Topp ${top.length}${filterNote}${opNote}</span>
      </div>`;

    const HOME_RATE = 2.0;

    top.forEach((s, i) => {
      const speedClass = s.maxEffKw >= 100 ? "fast" : s.maxEffKw >= 22 ? "medium" : "slow";
      const kwClass    = speedClass === "fast" ? "kw-fast" : speedClass === "medium" ? "kw-medium" : "kw-slow";
      const rawPrice   = s.chargepricePerKwh || s.usageCost || "";
      const isFree     = rawPrice.toLowerCase().includes("gratis") || rawPrice.toLowerCase().includes("free");
      const car        = state.carIndex !== null ? state.cars[state.carIndex] : null;
      const battery    = car?.batteryKwh ?? null;
      const realMil    = car?.rangeKm ? Math.round(car.rangeKm / 10 * 0.85) : null;
      const isEur      = rawPrice.includes("EUR");
      const priceNum   = rawPrice.match(/[\d,.]+/)?.[0] ? parseFloat(rawPrice.match(/[\d,.]+/)[0].replace(",",".")) : null;
      const priceKr    = (priceNum && isEur) ? priceNum * 11.5 : priceNum;
      const fullCost   = (battery && priceKr && !isFree) ? Math.round(battery * priceKr) : null;
      const krPerMil   = (fullCost && realMil) ? Math.round(fullCost / realMil) : null;
      const homeCost   = battery ? Math.round(battery * HOME_RATE) : null;
      const extraKr    = (fullCost && homeCost && fullCost > homeCost) ? fullCost - homeCost : null;
      const costHint   = fullCost
        ? `<div class="ev-cost-hint">🔋 Full laddning ~${fullCost} kr${krPerMil ? " · ~" + krPerMil + " kr/mil" : ""}</div>`
        : "";
      const homeHint   = extraKr
        ? `<div class="ev-home-compare">🏠 +${extraKr} kr jämfört med hemmaladdning</div>`
        : "";
      const displayPrice = (isEur && priceKr) ? `${priceKr.toFixed(2)} kr/kWh` : rawPrice;
      const priceBadgeCls = isFree ? " free" : "";
      const priceBadge = rawPrice ? `<div class="ev-price-badge${priceBadgeCls}">${displayPrice}</div>${costHint}${homeHint}` : "";
      const addr       = s.address ? `<div class="ev-station-addr">${s.address}</div>` : "";
      const opTag      = s.operator && !s.operator.includes("Unknown")
        ? `<span class="ev-operator-tag">${s.operator}</span>` : "";
      const connTag    = s.connectorCount > 0
        ? `<span class="ev-conncount-tag">🔌 ${s.connectorCount} laddpunkter</span>` : "";
      const isFav      = state.favorites.some(f => Math.abs(f.lat - s.lat) < 0.0005);
      const favClass   = isFav ? " saved" : "";
      const favTitle   = isFav ? "Ta bort favorit" : "Spara som favorit";
      const isDC       = s.connectorType.includes("DC");
      const timeMin    = (battery && isDC && s.maxEffKw > 0)
          ? Math.round((battery * 0.60) / s.maxEffKw * 60) : null;
      const timeStr    = timeMin === null ? null
          : timeMin < 60 ? `~${timeMin} min`
          : `~${Math.floor(timeMin/60)} tim ${timeMin % 60} min`;
      const timeTag    = timeStr ? `<span class="ev-time-tag">⏱ ${timeStr} (20→80%)</span>` : "";

      const _appEl = document.querySelector('.ev-app') || document.getElementById('ev-output');
      const isMob = (_appEl ? _appEl.offsetWidth : window.innerWidth) <= 600;
      if (isMob) {
        const priceBadgeMob = rawPrice ? `<span class="ev-price-badge${priceBadgeCls}" style="font-size:.7rem;padding:2px 6px;">${displayPrice}</span>` : "";
        stationsHtml += `
          <div class="ev-station">
            <div class="ev-station-bar ${speedClass}"></div>
            <div style="flex:1;padding:10px 12px;min-width:0;">
              <div style="display:flex;align-items:flex-start;gap:8px;min-width:0;">
                <div class="ev-rank" style="flex-shrink:0;margin-top:2px;">${i+1}</div>
                <div style="flex:1;min-width:0;">
                  <div class="ev-station-name" style="white-space:normal;line-height:1.35;">${s.name}</div>
                  ${addr}
                  <div style="display:flex;flex-wrap:wrap;gap:5px;align-items:center;margin-top:6px;">
                    <span class="ev-kw-badge ${kwClass}">⚡ ${Math.round(s.maxEffKw)} kW</span>
                    <span class="ev-connector-tag">${s.connectorType}</span>
                    ${opTag}
                    <span class="ev-dist" style="font-size:.73rem;font-weight:700;color:var(--muted);">${s.distanceKm.toFixed(1)} km</span>
                    ${priceBadgeMob}
                    ${connTag}${timeTag}
                    <button class="ev-fav-btn${favClass}" data-lat="${s.lat}" data-lon="${s.lon}" title="${favTitle}" style="margin-left:auto;">♥</button>
                  </div>
                </div>
              </div>
            </div>
          </div>`;
      } else {
        stationsHtml += `
          <div class="ev-station">
            <div class="ev-station-bar ${speedClass}"></div>
            <div class="ev-station-body">
              <div class="ev-rank">${i+1}</div>
              <div class="ev-station-main">
                <div class="ev-station-name">${s.name}</div>
                ${addr}
              </div>
              <div class="ev-station-right">
                <div class="ev-dist">${s.distanceKm.toFixed(1)} km</div>
                ${priceBadge}
                <button class="ev-fav-btn${favClass}" data-lat="${s.lat}" data-lon="${s.lon}" title="${favTitle}">♥</button>
              </div>
              <div class="ev-station-tags" style="grid-column:2">
                <span class="ev-kw-badge ${kwClass}">⚡ ${Math.round(s.maxEffKw)} kW</span>
                <span class="ev-connector-tag">${s.connectorType}</span>
                ${opTag}
                ${connTag}
                ${timeTag}
              </div>
            </div>
          </div>`;
      }
    });

    // Laddtidskalkylator
    let calcHtml = '';
    if (state.carIndex !== null && state.cars.length > 0) {
      const car = state.cars[state.carIndex];
      const dcStation = top.find(s => s.connectorType.includes('DC') && s.maxEffKw > 0);
      if (car && car.batteryKwh > 0) {
        const effKw = dcStation ? Math.min(car.maxDcKw || 50, dcStation.maxEffKw) : (car.maxDcKw || 50);
        const stLabel = dcStation
          ? (dcStation.name.length > 32 ? dcStation.name.slice(0, 30) + '…' : dcStation.name) + ' · ' + Math.round(dcStation.maxEffKw) + ' kW'
          : 'Ingen DC-station hittad';
        calcHtml = `
          <div class="ev-funfact-card" id="ev-calc-card" style="align-items:flex-start">
            <div class="ev-funfact-icon">⏱</div>
            <div style="flex:1">
              <div class="ev-funfact-label">Laddtidskalkylator — ${car.name}</div>
              <div style="margin:10px 0 12px;display:flex;gap:20px;flex-wrap:wrap;">
                <label style="flex:1;min-width:120px">
                  <div style="font-size:12px;color:rgba(147,197,253,0.7);margin-bottom:4px">Ladda från <span id="ev-calc-from-val">20</span>%</div>
                  <input type="range" id="ev-calc-from" min="0" max="90" value="20" oninput="evCalcUpdate()" style="width:100%;accent-color:#3b82f6">
                </label>
                <label style="flex:1;min-width:120px">
                  <div style="font-size:12px;color:rgba(147,197,253,0.7);margin-bottom:4px">Till <span id="ev-calc-to-val">80</span>%</div>
                  <input type="range" id="ev-calc-to" min="10" max="100" value="80" oninput="evCalcUpdate()" style="width:100%;accent-color:#3b82f6">
                </label>
              </div>
              <div id="ev-calc-result" style="background:rgba(255,255,255,0.04);border:1px solid rgba(59,130,246,0.2);border-radius:8px;padding:10px 14px;"></div>
              <div style="font-size:11px;color:rgba(147,197,253,0.45);margin-top:8px">Station: ${stLabel} · Effektiv laddning: ${Math.round(effKw)} kW</div>
            </div>
          </div>`;
      }
    }

    setOutput(html + stationsHtml + factHtml + calcHtml);

    if (state.lat && state.lon && top.length > 0)
      setTimeout(() => renderMap(state.lat, state.lon, top), 50);
  }

  function setOutput(html) {
    document.getElementById("ev-output").innerHTML = html;

    if (document.getElementById('ev-calc-card')) evCalcUpdate();

    document.querySelectorAll(".ev-op-chip").forEach(btn => {
      btn.addEventListener("click", () => {
        state.operatorFilter = btn.dataset.op || null;
        if (state.lastData) renderResults(state.lastData);
      });
    });

    document.querySelectorAll(".ev-fav-btn").forEach(btn => {
      btn.addEventListener("click", async () => {
        const lat = parseFloat(btn.dataset.lat);
        const lon = parseFloat(btn.dataset.lon);
        const station = state.lastData?.stations.find(s => Math.abs(s.lat - lat) < 0.0005);
        if (station) await toggleFavorite(station);
      });
    });

    document.querySelectorAll(".ev-fav-remove").forEach(btn => {
      btn.addEventListener("click", async () => {
        const id = parseInt(btn.dataset.id);
        const fav = state.favorites.find(f => f.id === id);
        if (fav) await toggleFavorite({ lat: fav.lat, lon: fav.lon, name: fav.name, address: fav.address, maxEffKw: fav.maxEffKw, connectorType: fav.connectorType, operator: fav.operator });
      });
    });

    function evFadeSlide(slides, dots, next, total) {
      const prev = slides[next._prev !== undefined ? next._prev : -1];
      slides.forEach((s, i) => {
        if (i === next._idx) {
          s.style.display = 'flex';
          s.classList.remove('ev-slide-leaving');
          s.classList.add('ev-slide-entering');
          setTimeout(() => s.classList.remove('ev-slide-entering'), 550);
        } else if (s === prev) {
          s.classList.add('ev-slide-leaving');
          setTimeout(() => { s.style.display = 'none'; s.classList.remove('ev-slide-leaving'); }, 360);
        } else {
          s.style.display = 'none';
        }
      });
      dots.forEach((d, i) => d.classList.toggle('ev-fact-dot-active', i === next._idx));
    }

    const funfactCarousel = document.getElementById('ev-funfact-carousel');
    if (funfactCarousel) {
      let fc = 0;
      const fSlides = Array.from(funfactCarousel.querySelectorAll('.ev-funfact-slide'));
      const fDots   = Array.from(funfactCarousel.querySelectorAll('.ev-fact-dot'));
      const ftotal  = fSlides.length;
      let ftimer;
      function showF(n) {
        const prev = fc;
        fc = (n + ftotal) % ftotal;
        fSlides[fc]._idx = fc; fSlides[fc]._prev = prev;
        evFadeSlide(fSlides, fDots, fSlides[fc], ftotal);
      }
      function startF() { clearInterval(ftimer); ftimer = setInterval(() => showF(fc + 1), 9000); }
      fDots.forEach((d, i) => d.addEventListener('click', () => { showF(i); startF(); }));
      startF();
    }

    const carousel = document.getElementById('ev-fact-carousel');
    if (carousel) {
      let current = 0;
      const allSlides = Array.from(carousel.querySelectorAll('.ev-fact-slide'));
      const allDots   = Array.from(carousel.querySelectorAll('.ev-fact-dot'));
      const total = allSlides.length;
      let timer = null;

      function showSlide(n) {
        const prev = current;
        current = (n + total) % total;
        allSlides[current]._idx = current; allSlides[current]._prev = prev;
        evFadeSlide(allSlides, allDots, allSlides[current], total);
        scrollToSelectedRow();
      }

      function startAuto() {
        clearInterval(timer);
        timer = setInterval(() => showSlide(current + 1), 9000);
      }

      function scrollToSelectedRow() {
        const activeSlide = allSlides[current];
        if (!activeSlide) return;
        const highlighted = activeSlide.querySelector('tr[style*="rgba(59,130,246"]');
        if (!highlighted) return;
        const tableWrap = highlighted.closest('[style*="overflow"]') || highlighted.closest('div');
        setTimeout(() => {
          if (tableWrap && tableWrap.scrollHeight > tableWrap.clientHeight) {
            tableWrap.scrollTop = highlighted.offsetTop - tableWrap.offsetTop - 40;
          }
        }, 200);
      }

      carousel.querySelector('#ev-fact-prev')?.addEventListener('click', () => { showSlide(current - 1); startAuto(); });
      carousel.querySelector('#ev-fact-next')?.addEventListener('click', () => { showSlide(current + 1); startAuto(); });
      allDots.forEach((d, i) => d.addEventListener('click', () => { showSlide(i); startAuto(); }));
      startAuto();
    }
  }

  // ===== CHATTBOT =====
  let chatHistory = (function(){ try{ return JSON.parse(localStorage.getItem('ev-chat')||'[]'); }catch(e){ return []; } })();
  let evChatExpanded = (function(){ try{ return localStorage.getItem('ev-chat-max') === '1'; }catch(e){ return false; } })();

  // ── Demospärr: utloggade får EV_DEMO_MAX gratis chattfrågor ────────
  // Prenumerationsfrågor (Vad ingår-knappen) och omförsök räknas inte.
  // Samma konto som Bilrådgivningen/Bränslekostnad via ca_token på elitrobban.se.
  var EV_DEMO_MAX = 3;
  var evAuthValid = null; // null = ej serververifierad; true/false = svar från /api/auth/me
  function evIsLoggedIn() {
    if (document.body.classList.contains('logged-in')) return true; // WP-inloggad
    if (evAuthValid !== null) return evAuthValid;                   // serververifierat
    return !!localStorage.getItem('ca_token');                      // optimistiskt innan koll
  }
  function evDemoRemaining() {
    var used = parseInt(localStorage.getItem('ev_demo_count') || '0', 10);
    return Math.max(0, EV_DEMO_MAX - used);
  }
  function evConsumeDemo() {
    var used = parseInt(localStorage.getItem('ev_demo_count') || '0', 10);
    localStorage.setItem('ev_demo_count', Math.min(used + 1, EV_DEMO_MAX));
    evUpdateChatDemoUI();
  }
  function evUpdateChatDemoUI() {
    var bar = document.getElementById('ev-chat-demobar');
    if (!bar) return;
    bar.style.display = 'flex'; // alltid synlig — Info & prenumeration-knappen ska alltid finnas
    var info = document.getElementById('ev-chat-demoinfo');
    if (evIsLoggedIn()) { if (info) info.textContent = ''; return; } // dölj demoräknaren, behåll knappen
    var left = document.getElementById('ev-chat-demoleft');
    var rem = evDemoRemaining();
    if (left) left.textContent = rem;
    if (info) info.innerHTML = rem > 0
      ? 'Demoläge · <b>' + rem + ' gratis fråg' + (rem === 1 ? 'a' : 'or') + ' kvar</b>'
      : 'Demo slut · <b>logga in för fler frågor</b>';
  }

  // Statiskt info-kort i chatten — ingen AI/backend-anrop, räknas aldrig mot demogränsen
  function evShowSubscriptionInfo() {
    var msgs = document.getElementById("ev-chat-messages");
    if (!msgs) return;
    var outer = document.createElement("div");
    var bubble = document.createElement("div");
    bubble.className = "ev-chat-bubble bot";
    bubble.innerHTML =
      '<div style="font-weight:800;margin-bottom:6px">💳 Prenumeration – 49 kr/mån</div>' +
      '<div style="font-size:.82rem;opacity:.85;margin-bottom:8px">Utan konto är tjänsterna begränsade (demoläge). Som prenumerant får du <b>allt obegränsat</b>:</div>' +
      '<div style="display:flex;flex-direction:column;gap:5px;font-size:.82rem">' +
        '<div>🚗 <b>AI Bilrådgivning</b> – obegränsad chatt och bilförslag</div>' +
        '<div>⚡ <b>AI EV Laddassistent</b> – obegränsad chatt, laddstationer och ruttplanering</div>' +
        '<div>⛽ <b>Bränslekostnadsberäkning</b> – obegränsade beräkningar</div>' +
      '</div>' +
      '<div style="font-size:.75rem;opacity:.6;margin-top:8px">Avbryt när som helst.</div>';
    var cta = document.createElement("button");
    cta.textContent = "Prenumerera – 49 kr/mån →";
    cta.style.cssText = "margin-top:10px;width:100%;background:linear-gradient(135deg,#2563eb,#4f46e5);color:#fff;border:none;border-radius:9px;padding:9px 14px;font-size:.82rem;font-weight:800;cursor:pointer";
    cta.onclick = evOpenSubscribePopup;
    bubble.appendChild(cta);
    outer.appendChild(bubble);
    msgs.appendChild(outer);
    msgs.scrollTop = msgs.scrollHeight;
    if (!chatIsOpen()) chatSetOpen(true); // öppna chatten om stängd
  }
  function evVerifyLogin() {
    var token = localStorage.getItem('ca_token');
    if (!token) { evAuthValid = false; evUpdateChatDemoUI(); return; }
    fetch('https://caradvice.onrender.com/api/auth/me', { headers: { 'Authorization': 'Bearer ' + token } })
      .then(function(res) {
        if (res.ok) { evAuthValid = true; return res.json().then(function(u){ if (u && u.subscriptionStatus) localStorage.setItem('ca_status', u.subscriptionStatus); }); }
        if (res.status === 401 || res.status === 403) {
          evAuthValid = false;
          localStorage.removeItem('ca_token'); localStorage.removeItem('ca_email'); localStorage.removeItem('ca_status');
        }
      }).catch(function(){}).then(function(){ evUpdateChatDemoUI(); });
  }
  function evOpenSubscribePopup() {
    if (window.evOpenSubscribe) { window.evOpenSubscribe(); return; }
    window.open('https://caradvice.onrender.com/subscribe.html?from=elbilsladdning', '_blank', 'width=480,height=650,resizable=yes');
  }

  function initChat() {
    const style = document.createElement("style");
    style.textContent = `
      .ev-chat-fab-wrap {
        position:fixed;bottom:24px;right:24px;z-index:9999;
        display:flex;flex-direction:column;align-items:center;gap:6px;
      }
      .ev-chat-fab-label {
        background:rgba(251,191,36,0.15);border:1px solid rgba(251,191,36,0.35);
        color:#fbbf24;font-size:11px;font-weight:700;padding:3px 10px;
        border-radius:20px;white-space:nowrap;letter-spacing:0.04em;
        animation:ev-label-pulse 3s ease-in-out infinite;
      }
      @keyframes ev-label-pulse {
        0%,100%{opacity:.7;transform:translateY(0)}
        50%{opacity:1;transform:translateY(-2px)}
      }
      .ev-chat-fab-ring {
        position:relative;display:flex;align-items:center;justify-content:center;
      }
      .ev-chat-spark {
        position:absolute;font-size:14px;line-height:1;pointer-events:none;
        animation:ev-spark 2.4s ease-in-out infinite;
      }
      .ev-chat-spark:nth-child(1){top:-18px;left:50%;transform:translateX(-50%);animation-delay:0s;}
      .ev-chat-spark:nth-child(2){top:18px;left:-20px;animation-delay:.8s;}
      .ev-chat-spark:nth-child(3){top:18px;right:-20px;animation-delay:1.6s;}
      @keyframes ev-spark {
        0%,100%{opacity:.3;transform:scale(.8) translateY(0);}
        50%{opacity:1;transform:scale(1.2) translateY(-4px);}
      }
      .ev-chat-fab {
        width:56px;height:56px;border-radius:18px;
        background:linear-gradient(145deg,#1e3a8a,#2563eb);
        border:none;cursor:pointer;
        box-shadow:0 4px 16px rgba(29,78,216,.55);
        display:flex;align-items:center;justify-content:center;
        transition:transform .15s,box-shadow .15s;
      }
      .ev-chat-fab:hover{transform:scale(1.1);box-shadow:0 6px 22px rgba(29,78,216,.7);}
      .ev-chat-panel {
        position:fixed;bottom:92px;right:24px;z-index:9998;
        width:390px;
        max-height:min(560px, calc(100vh - 130px));
        max-height:min(560px, calc(100dvh - 130px));
        background:rgba(5,10,24,0.82);
        backdrop-filter:blur(32px) saturate(1.4);-webkit-backdrop-filter:blur(32px) saturate(1.4);
        border:1px solid rgba(147,197,253,0.22);border-radius:22px;
        box-shadow:0 12px 60px rgba(0,0,0,.75),0 0 0 1px rgba(255,255,255,0.05) inset,0 1px 0 rgba(147,197,253,0.12) inset;
        display:none;flex-direction:column;overflow:hidden;
        font-family:inherit;
        animation:ev-panel-in .2s cubic-bezier(.22,1,.36,1);
      }
      /* Oppet/stangt bor i en klass pa body, inte i panelens inline style.
         Da kan FAB-regeln nedan uttrycka "expanderad OCH oppen" i ren CSS
         i stallet for att JS ska spegla tillstandet at den. */
      body.ev-chat-open .ev-chat-panel{display:flex;}
      @keyframes ev-panel-in{from{opacity:0;transform:translateY(12px) scale(.97)}to{opacity:1;transform:none}}
      .ev-chat-header {
        background:linear-gradient(135deg,rgba(17,40,110,0.95),rgba(29,78,216,0.88));
        backdrop-filter:blur(12px);-webkit-backdrop-filter:blur(12px);
        border-bottom:1px solid rgba(147,197,253,0.18);
        color:#fff;padding:14px 16px 13px;
        display:flex;align-items:center;justify-content:space-between;
        flex-shrink:0;gap:8px;
      }
      .ev-chat-header-title { display:flex;flex-direction:column;gap:1px; }
      .ev-chat-header-name { font-weight:700;font-size:14px;display:flex;align-items:center;gap:7px; }
      .ev-chat-header-online {
        width:7px;height:7px;border-radius:50%;background:#22c55e;flex-shrink:0;
        box-shadow:0 0 6px rgba(34,197,94,0.8);
        animation:ev-pulse-green 2s ease-in-out infinite;
      }
      @keyframes ev-pulse-green{0%,100%{box-shadow:0 0 6px rgba(34,197,94,0.8)}50%{box-shadow:0 0 12px rgba(34,197,94,1)}}
      .ev-chat-header-sub { font-size:10px;font-weight:500;color:rgba(147,197,253,0.65);letter-spacing:.04em; }
      .ev-chat-header-actions { display:flex;align-items:center;gap:6px; }
      .ev-chat-header-clear {
        background:rgba(255,255,255,0.08);border:1px solid rgba(255,255,255,0.16);
        color:rgba(255,255,255,0.7);font-size:11px;font-weight:600;padding:4px 10px;
        border-radius:20px;cursor:pointer;transition:all .15s;white-space:nowrap;
      }
      .ev-chat-header-clear:hover { background:rgba(255,255,255,0.15);color:#fff;border-color:rgba(255,255,255,0.28); }
      .ev-chat-header-close {
        background:rgba(255,255,255,0.07);border:1px solid rgba(255,255,255,0.13);
        color:rgba(255,255,255,0.7);font-size:15px;width:26px;height:26px;
        border-radius:50%;cursor:pointer;display:flex;align-items:center;justify-content:center;
        padding:0;line-height:1;transition:all .12s;
      }
      .ev-chat-header-close:hover { background:rgba(255,255,255,0.18);color:#fff; }
      .ev-chat-header-expand {
        background:rgba(255,255,255,0.07);border:1px solid rgba(255,255,255,0.13);
        color:rgba(255,255,255,0.7);width:26px;height:26px;
        border-radius:50%;cursor:pointer;display:flex;align-items:center;justify-content:center;
        padding:0;line-height:0;transition:all .12s;
      }
      .ev-chat-header-expand:hover { background:rgba(255,255,255,0.18);color:#fff; }
      .ev-chat-header-expand svg { display:block;transition:transform .18s; }
      body.ev-chat-max .ev-chat-header-expand svg { transform:rotate(180deg); }
      .ev-chat-messages {
        flex:1;overflow-y:auto;padding:16px 13px;
        display:flex;flex-direction:column;gap:11px;
        background:transparent;min-height:0;
      }
      .ev-chat-messages::-webkit-scrollbar { width:3px; }
      .ev-chat-messages::-webkit-scrollbar-track { background:transparent; }
      .ev-chat-messages::-webkit-scrollbar-thumb { background:rgba(147,197,253,0.2);border-radius:3px; }
      .ev-chat-bubble {
        max-width:84%;padding:10px 14px;border-radius:16px;
        font-size:13px;line-height:1.65;word-break:break-word;
      }
      .ev-chat-bubble.bot {
        background:rgba(10,18,40,0.75);
        backdrop-filter:blur(8px);-webkit-backdrop-filter:blur(8px);
        border:1px solid rgba(147,197,253,0.14);
        border-radius:4px 16px 16px 16px;align-self:flex-start;color:#dde8ff;
        box-shadow:0 2px 12px rgba(0,0,0,.25);
      }
      .ev-chat-bubble.bot strong { color:#93c5fd; }
      .ev-chat-bubble.bot ul { margin:6px 0 2px 16px;padding:0;display:flex;flex-direction:column;gap:3px; }
      .ev-chat-bubble.bot li { list-style:disc; }
      .ev-chat-bubble.user {
        background:linear-gradient(135deg,#1d4ed8,#3b82f6);
        border:none;
        color:#fff;border-radius:16px 16px 4px 16px;align-self:flex-end;
        box-shadow:0 3px 14px rgba(59,130,246,0.4);
      }
      .ev-chat-quick {
        padding:10px 13px 6px;display:flex;flex-wrap:wrap;gap:7px;flex-shrink:0;
        background:rgba(4,8,20,0.55);border-top:1px solid rgba(147,197,253,0.1);
      }
      /* JS-styrt lage: satts nar samtalet borjat sa att snabbknapparna forsvinner.
         Egen klass i stallet for inline display, annars kan CSS-reglerna nedan
         (platsbrist i liggande lage, expanderat lage) aldrig ta over. */
      .ev-chat-quick.ev-chat-quick-off { display:none; }
      .ev-chat-quick-btn {
        background:rgba(59,130,246,0.1);border:1px solid rgba(147,197,253,0.22);color:#93c5fd;
        border-radius:20px;padding:5px 13px;font-size:12px;font-weight:600;
        cursor:pointer;transition:all .15s;white-space:nowrap;
        backdrop-filter:blur(4px);-webkit-backdrop-filter:blur(4px);
      }
      .ev-chat-quick-btn:hover { background:rgba(59,130,246,0.28);color:#fff;border-color:rgba(147,197,253,0.5); }
      .ev-chat-input-row {
        display:flex;gap:8px;padding:10px 13px 11px;
        border-top:1px solid rgba(147,197,253,0.1);
        background:rgba(4,8,20,0.55);flex-shrink:0;
      }
      .ev-chat-input {
        flex:1;border:1.5px solid rgba(147,197,253,0.18);border-radius:22px;
        padding:9px 15px;font-size:13px;outline:none;
        background:rgba(10,18,40,0.7);color:#f0f4ff;transition:border-color .15s,box-shadow .15s;
      }
      .ev-chat-input::placeholder { color:rgba(200,215,255,0.3); }
      .ev-chat-input:focus { border-color:rgba(147,197,253,0.55);box-shadow:0 0 0 3px rgba(59,130,246,0.15); }
      .ev-chat-send {
        width:40px;height:40px;border-radius:50%;
        background:linear-gradient(135deg,#1d4ed8,#3b82f6);
        color:#fff;border:none;cursor:pointer;
        font-size:16px;display:flex;align-items:center;justify-content:center;
        flex-shrink:0;transition:all .15s;
        box-shadow:0 2px 12px rgba(59,130,246,0.45);
      }
      .ev-chat-send:hover { background:linear-gradient(135deg,#2563eb,#60a5fa);box-shadow:0 4px 18px rgba(59,130,246,0.6);transform:scale(1.06); }
      .ev-chat-typing { display:flex;gap:4px;align-items:center;padding:4px 0; }
      .ev-chat-typing span {
        width:7px;height:7px;border-radius:50%;background:rgba(147,197,253,0.45);
        animation:ev-bounce .9s infinite;display:inline-block;
      }
      .ev-chat-typing span:nth-child(2) { animation-delay:.15s; }
      .ev-chat-typing span:nth-child(3) { animation-delay:.3s; }
      @keyframes ev-bounce { 0%,80%,100%{transform:translateY(0)} 40%{transform:translateY(-6px)} }
      .ev-chat-cursor{display:inline-block;width:2px;height:13px;background:#93c5fd;margin-left:2px;border-radius:1px;animation:ev-cursor-blink .55s steps(1) infinite;vertical-align:middle;}
      @keyframes ev-cursor-blink{0%,100%{opacity:1}50%{opacity:0}}
      .ev-chat-feedback{display:flex;gap:6px;margin-top:6px;padding-left:2px;align-items:center;}
      .ev-chat-thumb{background:none;border:1px solid rgba(147,197,253,0.16);color:rgba(147,197,253,0.35);font-size:12px;padding:2px 8px;border-radius:10px;cursor:pointer;transition:all .15s;line-height:1.5;}
      .ev-chat-thumb:hover{border-color:rgba(147,197,253,0.5);color:#93c5fd;}
      .ev-chat-thumb.voted{border-color:rgba(147,197,253,0.6);color:#93c5fd;background:rgba(147,197,253,0.08);}
      .ev-chat-retry{background:none;border:1px solid rgba(239,68,68,0.3);color:rgba(239,68,68,0.65);font-size:11px;font-weight:600;padding:4px 11px;border-radius:20px;cursor:pointer;margin-top:7px;display:inline-block;transition:all .15s;}
      .ev-chat-retry:hover{border-color:rgba(239,68,68,0.6);color:#ef4444;}
      @media(max-width:640px){
        /* Panelen ska vara ett kort i underkanten — inte ta over hela skarmen */
        .ev-chat-panel{
          width:auto;left:10px;right:10px;bottom:88px;border-radius:18px;
          max-height:min(440px, 58vh);
          max-height:min(440px, 58dvh);
        }
        .ev-chat-fab-wrap{right:12px;bottom:12px;gap:4px;}
        .ev-chat-fab{width:48px;height:48px;border-radius:15px;}
        .ev-chat-fab svg{width:29px;height:34px;}
        .ev-chat-fab-label{font-size:10px;padding:2px 8px;}
        .ev-chat-spark{font-size:12px;}
        .ev-chat-spark:nth-child(2){top:15px;left:-16px;}
        .ev-chat-spark:nth-child(3){top:15px;right:-16px;}
        .ev-chat-header{padding:11px 12px 10px;}
        .ev-chat-header-name{font-size:13px;}
        .ev-chat-messages{padding:12px 10px;gap:9px;}
        .ev-chat-bubble{max-width:90%;padding:9px 12px;}
        .ev-chat-quick{padding:8px 10px 4px;gap:6px;}
        .ev-chat-quick-btn{font-size:11px;padding:4px 11px;}
        .ev-chat-input-row{padding:8px 10px 9px;}
      }
      /* Liggande mobil: nastan ingen hojd kvar — hall panelen riktigt lag */
      @media(max-width:900px) and (max-height:480px){
        .ev-chat-panel{bottom:72px;max-height:min(300px, 70vh);max-height:min(300px, 70dvh);}
        .ev-chat-quick{display:none;}
        .ev-chat-fab-label{display:none;}
        .ev-chat-fab{width:44px;height:44px;}
        .ev-chat-fab svg{width:27px;height:31px;}
      }
      /* Expanderat lage. Klassen sitter pa body sa att aven .ev-chat-fab-wrap gar att na,
         och sa att specificiteten (0,2,1) slar bade bas- och mediaregler ovan. */
      body.ev-chat-max .ev-chat-panel{
        width:min(560px, calc(100vw - 48px));
        max-height:calc(100vh - 120px);
        max-height:calc(100dvh - 120px);
      }
      /* Smal eller lag skarm: expanderat = helskarmsark, FAB:en i vagen doljs */
      @media(max-width:640px),(max-height:480px){
        body.ev-chat-max .ev-chat-panel{
          left:8px;right:8px;top:8px;bottom:8px;
          width:auto;max-height:none;border-radius:16px;
        }
        body.ev-chat-open.ev-chat-max .ev-chat-fab-wrap{display:none;}
        body.ev-chat-max .ev-chat-quick:not(.ev-chat-quick-off){display:flex;}
      }
    `;
    document.head.appendChild(style);

    const root = document.createElement("div");
    root.innerHTML = `
      <div class="ev-chat-fab-wrap">
        <span class="ev-chat-fab-label">✨ Fråga AI</span>
        <div class="ev-chat-fab-ring">
          <span class="ev-chat-spark">⚡</span>
          <span class="ev-chat-spark">⚡</span>
          <span class="ev-chat-spark">⚡</span>
        <button class="ev-chat-fab" id="ev-chat-fab" title="Fråga EV-assistenten">
          <svg viewBox="0 0 44 52" width="34" height="40" xmlns="http://www.w3.org/2000/svg">
            <defs>
              <radialGradient id="hg" cx="38%" cy="32%"><stop offset="0%" stop-color="#fef3c7"/><stop offset="100%" stop-color="#f59e0b"/></radialGradient>
              <radialGradient id="bg" cx="38%" cy="30%"><stop offset="0%" stop-color="#fde68a"/><stop offset="100%" stop-color="#d97706"/></radialGradient>
            </defs>
            <!-- antenna -->
            <line x1="22" y1="1" x2="22" y2="6" stroke="#92400e" stroke-width="1.8" stroke-linecap="round"/>
            <circle cx="22" cy="1" r="2" fill="#fbbf24"/>
            <!-- head -->
            <ellipse cx="22" cy="13" rx="10" ry="9" fill="url(#hg)" stroke="#d97706" stroke-width="0.8"/>
            <!-- eyes -->
            <ellipse cx="18.5" cy="12" rx="2" ry="2.2" fill="#1e3a8a"/>
            <ellipse cx="25.5" cy="12" rx="2" ry="2.2" fill="#1e3a8a"/>
            <circle cx="19.2" cy="11.2" r="0.7" fill="#fff"/>
            <circle cx="26.2" cy="11.2" r="0.7" fill="#fff"/>
            <!-- smile -->
            <path d="M17.5 16.5 Q22 20.5 26.5 16.5" stroke="#92400e" stroke-width="1.4" fill="none" stroke-linecap="round"/>
            <!-- neck -->
            <rect x="19.5" y="21" width="5" height="3" rx="1" fill="#f59e0b"/>
            <!-- body (3 rolls like michelin) -->
            <ellipse cx="22" cy="29" rx="12" ry="7" fill="url(#bg)" stroke="#d97706" stroke-width="0.7"/>
            <ellipse cx="22" cy="37" rx="10" ry="6" fill="url(#bg)" stroke="#d97706" stroke-width="0.7"/>
            <ellipse cx="22" cy="44" rx="8" ry="5" fill="url(#bg)" stroke="#d97706" stroke-width="0.7"/>
            <!-- arms -->
            <ellipse cx="9" cy="31" rx="4.5" ry="6.5" fill="url(#bg)" stroke="#d97706" stroke-width="0.7" transform="rotate(-25 9 31)"/>
            <ellipse cx="35" cy="31" rx="4.5" ry="6.5" fill="url(#bg)" stroke="#d97706" stroke-width="0.7" transform="rotate(25 35 31)"/>
            <!-- lightning bolt on chest -->
            <path d="M20 26 L17 33 L21.5 31 L19 38" fill="#1e3a8a" stroke="#1e3a8a" stroke-width="0.5" stroke-linejoin="round"/>
            <!-- sparks on arms -->
            <text x="4" y="28" font-size="7" fill="#fef08a">⚡</text>
            <text x="32" y="28" font-size="7" fill="#fef08a">⚡</text>
          </svg>
        </button>
        </div>
      </div>
      <div class="ev-chat-panel" id="ev-chat-panel">
        <div class="ev-chat-header">
          <div class="ev-chat-header-title">
            <div class="ev-chat-header-name">
              <span class="ev-chat-header-online"></span>⚡ EV-Assistenten
            </div>
            <div class="ev-chat-header-sub">AI • Laddning &amp; Elbilar</div>
          </div>
          <div class="ev-chat-header-actions">
            <button class="ev-chat-header-clear" id="ev-chat-clear">Rensa</button>
            <button class="ev-chat-header-expand" id="ev-chat-expand" title="Expandera chatten" aria-label="Expandera chatten" aria-expanded="false">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path d="M6 15l6-6 6 6"/></svg>
            </button>
            <button class="ev-chat-header-close" id="ev-chat-close">✕</button>
          </div>
        </div>
        <div class="ev-chat-messages" id="ev-chat-messages"></div>
        <div class="ev-chat-quick" id="ev-chat-quick">
          <button class="ev-chat-quick-btn" data-q="Ge mig råd för att köpa elbil">🚗 Elbilsköp</button>
          <button class="ev-chat-quick-btn" data-q="Vilken elbil laddar snabbast med DC?">⚡ Snabbaste DC</button>
          <button class="ev-chat-quick-btn" data-q="Vilken elbil har längst räckvidd?">🛣️ Räckvidd</button>
          <button class="ev-chat-quick-btn" data-q="Var laddar jag billigast?">💰 Billigast</button>
        </div>
        <div class="ev-chat-demobar" id="ev-chat-demobar" style="display:none;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:6px 8px;padding:7px 12px;font-size:.74rem;color:rgba(147,197,253,.78);border-top:1px solid rgba(148,163,184,.12);">
          <span id="ev-chat-demoinfo">Demoläge · <b><span id="ev-chat-demoleft">3</span> gratis frågor kvar</b></span>
          <button id="ev-chat-subbtn" type="button" style="background:rgba(59,130,246,.15);border:1px solid rgba(59,130,246,.32);color:#93c5fd;border-radius:8px;padding:4px 10px;font-size:.72rem;font-weight:700;cursor:pointer;white-space:nowrap;">💳 Info &amp; prenumeration</button>
        </div>
        <div class="ev-chat-input-row">
          <input class="ev-chat-input" id="ev-chat-input" type="text" placeholder="Skriv en fråga…" autocomplete="off" />
          <button class="ev-chat-send" id="ev-chat-send">
            <svg viewBox="0 0 20 20" width="18" height="18" fill="currentColor"><path d="M2 10.5l15-8-5 8 5 8z"/><path d="M17 2.5L9 10.5"/></svg>
          </button>
        </div>
      </div>
    `;
    document.body.appendChild(root);

    chatAppendBot("Undrar du vilken elbil du bör köpa? 🚗 Jag kan ge dig tips! Välj ett ämne nedan eller ställ en egen fråga.", false);

    if (chatHistory.length > 0) {
      document.getElementById("ev-chat-quick").classList.add("ev-chat-quick-off");
      chatHistory.forEach(function(m) {
        if (m.role === "user") chatAppendUser(m.content);
        else if (m.role === "assistant") chatAppendBot(m.content, false);
      });
    }

    document.getElementById("ev-chat-fab").addEventListener("click", chatToggle);
    document.getElementById("ev-chat-close").addEventListener("click", chatToggle);
    document.getElementById("ev-chat-expand").addEventListener("click", chatToggleExpanded);
    document.getElementById("ev-chat-send").addEventListener("click", chatSend);
    document.getElementById("ev-chat-input").addEventListener("keydown", e => { if (e.key === "Enter") chatSend(); });
    document.getElementById("ev-chat-clear").addEventListener("click", chatClear);
    document.querySelectorAll(".ev-chat-quick-btn").forEach(btn =>
      btn.addEventListener("click", () => chatSendMessage(btn.dataset.q))
    );

    document.body.classList.toggle("ev-chat-max", evChatExpanded);
    chatUpdateExpandBtn();

    // Info & prenumeration-knappen: statiskt kort, ingen AI/backend, alltid gratis
    var subBtn = document.getElementById("ev-chat-subbtn");
    if (subBtn) subBtn.addEventListener("click", evShowSubscriptionInfo);

    // Visa demoräknaren för utloggade + verifiera ev. inloggning mot servern
    evUpdateChatDemoUI();
    evVerifyLogin();

    // Inloggning via CarAdvice-popupen (samma konto som övriga tjänster)
    window.addEventListener("message", function(ev) {
      if (!ev.data || !ev.data.type) return;
      if (ev.data.type === "CA_LOGIN" || ev.data.type === "CA_SUBSCRIBED") {
        if (ev.data.token)  localStorage.setItem("ca_token", ev.data.token);
        if (ev.data.email)  localStorage.setItem("ca_email", ev.data.email);
        if (ev.data.status) localStorage.setItem("ca_status", ev.data.status);
        evAuthValid = null; evVerifyLogin();
      }
      if (ev.data.type === "CA_LOGOUT") {
        localStorage.removeItem("ca_token"); localStorage.removeItem("ca_email"); localStorage.removeItem("ca_status");
        evAuthValid = false; evUpdateChatDemoUI();
      }
    });
  }

  function chatIsOpen() {
    return document.body.classList.contains("ev-chat-open");
  }

  function chatSetOpen(open) {
    document.body.classList.toggle("ev-chat-open", open);
    if (open) document.getElementById("ev-chat-input").focus();
  }

  function chatToggle() {
    chatSetOpen(!chatIsOpen());
  }

  function chatUpdateExpandBtn() {
    const btn = document.getElementById("ev-chat-expand");
    if (!btn) return;
    const lbl = evChatExpanded ? "Minska chatten" : "Expandera chatten";
    btn.title = lbl;
    btn.setAttribute("aria-label", lbl);
    btn.setAttribute("aria-expanded", evChatExpanded ? "true" : "false");
  }

  function chatToggleExpanded() {
    evChatExpanded = !evChatExpanded;
    try { localStorage.setItem("ev-chat-max", evChatExpanded ? "1" : "0"); } catch(e) {}
    document.body.classList.toggle("ev-chat-max", evChatExpanded);
    chatUpdateExpandBtn();
    const msgs = document.getElementById("ev-chat-messages");
    if (msgs) msgs.scrollTop = msgs.scrollHeight;
  }

  function chatMarkdown(text) {
    return text
      .replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;")
      .replace(/\*\*(.+?)\*\*/g,"<strong>$1</strong>")
      .replace(/\*(.+?)\*/g,"<em>$1</em>")
      .replace(/^[-•]\s+(.+)$/gm,"<li>$1</li>")
      .replace(/(<li>.*<\/li>)/s, "<ul>$1</ul>")
      .replace(/\n/g,"<br>");
  }

  function chatAppendBot(text, animate) {
    const msgs = document.getElementById("ev-chat-messages");
    const outer = document.createElement("div");
    const bubble = document.createElement("div");
    bubble.className = "ev-chat-bubble bot";
    outer.appendChild(bubble);
    msgs.appendChild(outer);
    msgs.scrollTop = msgs.scrollHeight;
    if (animate !== false && text.length > 0) {
      let i = 0;
      const speed = Math.max(6, Math.min(20, 2600 / text.length));
      (function tick() {
        i += 3;
        if (i >= text.length) {
          bubble.innerHTML = chatMarkdown(text);
          evAddFeedback(outer);
          msgs.scrollTop = msgs.scrollHeight;
        } else {
          bubble.textContent = text.slice(0, i);
          const cur = document.createElement("span");
          cur.className = "ev-chat-cursor";
          bubble.appendChild(cur);
          msgs.scrollTop = msgs.scrollHeight;
          setTimeout(tick, speed);
        }
      })();
    } else {
      bubble.innerHTML = chatMarkdown(text);
      if (animate !== false) evAddFeedback(outer);
    }
    return outer;
  }

  function evAddFeedback(outer) {
    const fb = document.createElement("div");
    fb.className = "ev-chat-feedback";
    fb.innerHTML = '<button class="ev-chat-thumb">👍</button><button class="ev-chat-thumb">👎</button>';
    fb.querySelectorAll(".ev-chat-thumb").forEach(function(btn) {
      btn.addEventListener("click", function() {
        fb.querySelectorAll(".ev-chat-thumb").forEach(function(b) { b.classList.remove("voted"); });
        btn.classList.add("voted");
        setTimeout(function() { fb.innerHTML = '<span style="font-size:11px;color:rgba(147,197,253,0.45)">Tack!</span>'; }, 350);
      });
    });
    outer.appendChild(fb);
  }

  function buildStationContext() {
    const d = state.lastData;
    const car = state.carIndex !== null ? state.cars[state.carIndex] : null;
    const battery = car?.batteryKwh ?? null;
    const contextParts = [];

    // Selected car specs
    if (car) {
      const specParts = ["Vald bil: " + car.name];
      if (battery) specParts.push("batteri " + battery + " kWh");
      if (car.rangeKm) specParts.push("WLTP " + car.rangeKm + " km (~" + Math.round(car.rangeKm * 0.85) + " km verklig)");
      if (car.maxDcKw) specParts.push("DC max " + car.maxDcKw + " kW");
      if (car.maxAcKw) specParts.push("AC max " + car.maxAcKw + " kW");
      if (car.priceKr) specParts.push("pris ~" + Math.round(car.priceKr / 1000) + " 000 kr");
      contextParts.push(specParts.join(" · "));
    }

    // Charging time calculator
    const calc = state.lastCalc;
    if (calc) {
      let calcStr = "Laddtidskalkylator: " + calc.carName + " · " + calc.fromPct + "% → " + calc.toPct + "% (" + calc.kwhCharge + " kWh) · " + calc.effKw + " kW effektiv · tid " + calc.timeStr;
      if (calc.cost) calcStr += " · kostnad ~" + calc.cost + " kr";
      if (calc.rangeAdded) calcStr += " · räckvidd tillkommer ~" + calc.rangeAdded + " km";
      if (calc.stationName) calcStr += " · vid station: " + calc.stationName;
      contextParts.push(calcStr);
    }

    // Carousel facts (top 3 per category from car database)
    if (state.cars.length > 0) {
      const allValid = state.cars.filter(c => c.priceKr > 0 && c.rangeKm > 0);
      const topDc = state.cars.filter(c => c.maxDcKw > 0 && c.priceKr > 0).sort((a, b) => b.maxDcKw - a.maxDcKw).slice(0, 3);
      const topRange = allValid.sort((a, b) => b.rangeKm - a.rangeKm).slice(0, 3);
      if (topDc.length) contextParts.push("Snabbaste DC (topp 3): " + topDc.map(c => c.name + " " + c.maxDcKw + " kW").join(", "));
      if (topRange.length) contextParts.push("Längst räckvidd (topp 3): " + topRange.map(c => c.name + " " + c.rangeKm + " km").join(", "));
    }

    if (!d || !d.stations || d.stations.length === 0) {
      return contextParts.length ? contextParts.join("\n") : null;
    }
    contextParts.push("--- LADDSTATIONER (" + (state.city || "din position") + ") ---");
    contextParts.push("Bil i sökning: " + d.carName);
    if (battery) {
      contextParts.push("Hemmaladdning (billigaste): ~1,50–3,50 kr/kWh · full laddning ~" + Math.round(battery * 1.5) + "–" + Math.round(battery * 3.5) + " kr");
    }
    const stationsSorted = d.stations.slice(0, 5).map(function(s) {
      const raw = s.chargepricePerKwh || s.usageCost || "";
      const isEur = raw.includes("EUR");
      const num = raw.match(/[\d,.]+/)?.[0] ? parseFloat(raw.match(/[\d,.]+/)[0].replace(",",".")) : null;
      const priceKr = num ? (isEur ? num * 11.5 : num) : null;
      return Object.assign({}, s, { priceKr: priceKr });
    });
    stationsSorted.forEach(function(s, i) {
      const priceStr = s.chargepricePerKwh || s.usageCost || "okänt pris";
      const fullCost = (battery && s.priceKr) ? " · full laddning ~" + Math.round(battery * s.priceKr) + " kr" : "";
      contextParts.push((i+1) + ". " + s.name + " — " + s.distanceKm.toFixed(1) + " km — " + Math.round(s.maxEffKw) + " kW " + s.connectorType + " — " + priceStr + fullCost);
    });
    const cheapest = stationsSorted.filter(s => s.priceKr).sort((a, b) => a.priceKr - b.priceKr)[0];
    if (cheapest) contextParts.push("Billigaste publik station: " + cheapest.name + " (" + (cheapest.chargepricePerKwh || cheapest.usageCost) + ", " + cheapest.distanceKm.toFixed(1) + " km bort)");
    if (d.recommendation) contextParts.push("AI-rekommendation: " + d.recommendation);
    if (d.carFact) contextParts.push("Faktaruta visad: " + d.carFact);

    const r = state.lastRoute;
    if (r) {
      contextParts.push("--- PLANERAD RUTT ---");
      const durStr = r.durationMin ? " (~" + Math.floor(r.durationMin / 60) + " tim " + (r.durationMin % 60) + " min körtid)" : "";
      contextParts.push("Från: " + r.startDisplay + " → Till: " + r.destination + " · " + r.distanceKm + " km" + durStr + " · Bil: " + r.carName);
      if (r.stopsNeeded === 0) {
        contextParts.push("Bilen klarar sträckan utan laddningsstopp.");
      } else {
        contextParts.push("Antal laddningsstopp längs rutten: " + r.stopsNeeded);
        r.stops.forEach(function(stop, i) {
          const s = stop.station;
          const price = s.chargepricePerKwh || s.usageCost || "okänt pris";
          contextParts.push("Stopp " + (i+1) + ": " + s.name + " · " + Math.round(s.maxEffKw) + " kW " + s.connectorType + " · " + price + " · " + stop.distanceFromStartKm + " km från start");
        });
      }
    }

    return contextParts.join("\n");
  }

  function saveChatHistory() {
    try { localStorage.setItem("ev-chat", JSON.stringify(chatHistory.slice(-20))); } catch(e) {}
  }

  function chatClear() {
    chatHistory = [];
    try { localStorage.removeItem("ev-chat"); } catch(e) {}
    const msgs = document.getElementById("ev-chat-messages");
    msgs.innerHTML = "";
    document.getElementById("ev-chat-quick").classList.remove("ev-chat-quick-off");
    chatAppendBot("Undrar du vilken elbil du bör köpa? 🚗 Jag kan ge dig tips! Välj ett ämne nedan eller ställ en egen fråga.", false);
  }

  function chatAppendUser(text) {
    const msgs = document.getElementById("ev-chat-messages");
    const div = document.createElement("div");
    div.innerHTML = `<div class="ev-chat-bubble user">${text}</div>`;
    msgs.appendChild(div);
    msgs.scrollTop = msgs.scrollHeight;
  }

  function chatSend() {
    const input = document.getElementById("ev-chat-input");
    const msg = input.value.trim();
    if (!msg) return;
    input.value = "";
    chatSendMessage(msg);
  }

  function evAddFollowupChips(text, outer) {
    const lower = text.toLowerCase();
    let chips = [];
    if (/räckvidd|km|wltp/.test(lower))        chips.push("Vilken elbil har längst räckvidd?", "Räcker det för pendling?");
    else if (/ladda|laddning|kw/.test(lower))   chips.push("Hur fort laddar den?", "Var hittar jag snabbladdare?");
    else if (/pris|kr|budget|köpa/.test(lower)) chips.push("Finns billigare alternativ?", "Ny eller begagnad?");
    else if (/tesla/.test(lower))               chips.push("Tesla vs Kia EV6?", "Har Tesla supercharger i Sverige?");
    else if (/kia|hyundai|ioniq/.test(lower))   chips.push("Jämför med Tesla?", "Vilken räckvidd har den?");
    if (/driftkostnad|kostnad|el per mil/.test(lower) && chips.length < 2) chips.push("Vad kostar det per mil?");
    if (/motor|prestanda|acceleration/.test(lower) && chips.length < 2)    chips.push("Vilken elbil är snabbast?");
    if (chips.length === 0) chips = ["Vilken elbil passar mig?", "Vad kostar laddning per månad?"];
    const wrap = document.createElement("div");
    wrap.style.cssText = "display:flex;flex-wrap:wrap;gap:6px;margin-top:8px;";
    chips.slice(0, 3).forEach(function(chip) {
      const btn = document.createElement("button");
      btn.textContent = chip;
      btn.style.cssText = "padding:5px 11px;font-size:.72rem;background:rgba(59,130,246,.12);border:1px solid rgba(96,165,250,.25);border-radius:16px;color:#93c5fd;cursor:pointer;transition:background .15s;font-family:inherit;";
      btn.onmouseenter = function() { btn.style.background = "rgba(59,130,246,.22)"; };
      btn.onmouseleave = function() { btn.style.background = "rgba(59,130,246,.12)"; };
      btn.onclick = function() { wrap.remove(); document.getElementById("ev-chat-input").value = chip; chatSend(); };
      wrap.appendChild(btn);
    });
    outer.appendChild(wrap);
  }

  async function chatSendMessage(message, opts) {
    opts = opts || {};
    // Demospärr: utloggade får EV_DEMO_MAX frågor. opts.free = prenumerationsknappen
    // (alltid gratis), opts.retry = omförsök (ska inte dra en till).
    if (!opts.free && !opts.retry && !evIsLoggedIn() && evDemoRemaining() <= 0) {
      document.getElementById("ev-chat-quick").classList.add("ev-chat-quick-off");
      var gate = chatAppendBot("Du har använt dina " + EV_DEMO_MAX + " gratis frågor i demoläget. Logga in för obegränsad tillgång — eller klicka “Vad ingår?” nedan för att läsa om prenumerationen.", false);
      var lb = document.createElement("button");
      lb.className = "ev-chat-retry"; lb.textContent = "🔑 Logga in";
      lb.onclick = evOpenSubscribePopup; gate.appendChild(lb);
      evUpdateChatDemoUI();
      return;
    }
    if (!opts.free && !opts.retry && !evIsLoggedIn()) evConsumeDemo();
    document.getElementById("ev-chat-quick").classList.add("ev-chat-quick-off");
    chatAppendUser(message);
    chatHistory.push({ role: "user", content: message });
    saveChatHistory();

    const msgsEl = document.getElementById("ev-chat-messages");
    const typingDiv = document.createElement("div");
    typingDiv.innerHTML = `<div class="ev-chat-bubble bot"><div class="ev-chat-typing"><span></span><span></span><span></span></div></div>`;
    msgsEl.appendChild(typingDiv);
    msgsEl.scrollTop = msgsEl.scrollHeight;

    const context = buildStationContext();
    const limited = chatHistory.slice(-10);

    let resp;
    try {
      resp = await fetch(`${API}/api/chat/stream`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ messages: limited, context: context })
      });
    } catch (_) {
      typingDiv.remove();
      const errDiv = chatAppendBot("Kunde inte nå assistenten — kontrollera anslutningen.", false);
      const btn = document.createElement("button"); btn.className = "ev-chat-retry"; btn.textContent = "↺ Försök igen";
      btn.onclick = function() { errDiv.remove(); chatHistory.pop(); saveChatHistory(); chatSendMessage(message, {retry:true}); };
      errDiv.appendChild(btn);
      return;
    }

    typingDiv.remove();

    if (resp.status === 429) {
      chatAppendBot("Du har ställt för många frågor — vänta en minut och försök igen.", false);
      return;
    }
    if (!resp.ok) {
      const errDiv = chatAppendBot("Något gick fel (fel " + resp.status + ").", false);
      const btn = document.createElement("button"); btn.className = "ev-chat-retry"; btn.textContent = "↺ Försök igen";
      btn.onclick = function() { errDiv.remove(); chatHistory.pop(); saveChatHistory(); chatSendMessage(message, {retry:true}); };
      errDiv.appendChild(btn);
      return;
    }

    // Fallback for browsers without streaming support
    if (!resp.body || typeof resp.body.getReader !== "function") {
      try {
        const fb = await fetch(`${API}/api/chat`, {
          method: "POST", headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ messages: limited, context: context })
        });
        const fbData = await fb.json();
        const fbReply = fbData.reply || fbData.error || "Inget svar.";
        chatHistory.push({ role: "assistant", content: fbReply });
        saveChatHistory();
        const fbOuter = chatAppendBot(fbReply, true);
        evAddFollowupChips(fbReply, fbOuter);
      } catch (_) { chatAppendBot("Kunde inte nå assistenten.", false); }
      return;
    }

    // Streaming bubble
    const outer = document.createElement("div");
    const bubble = document.createElement("div");
    bubble.className = "ev-chat-bubble bot";
    outer.appendChild(bubble);
    msgsEl.appendChild(outer);

    let fullText = "";
    const reader = resp.body.getReader();
    const decoder = new TextDecoder();
    let buf = "";

    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buf += decoder.decode(value, { stream: true });
        const lines = buf.split("\n");
        buf = lines.pop();
        for (const line of lines) {
          const trimmed = line.trim();
          if (!trimmed.startsWith("data:")) continue;
          const data = trimmed.slice(5).trim();
          if (data === "[DONE]") break;
          try {
            const token = JSON.parse(data);
            if (token.startsWith("[ERR]")) throw new Error(token.slice(5));
            fullText += token;
            bubble.textContent = fullText;
            msgsEl.scrollTop = msgsEl.scrollHeight;
          } catch (parseErr) {
            if (parseErr.message && !parseErr.message.startsWith("JSON")) throw parseErr;
          }
        }
      }
    } catch (streamErr) {
      if (!fullText) {
        outer.remove();
        const errDiv = chatAppendBot(streamErr.message || "Kunde inte nå assistenten.", false);
        const btn = document.createElement("button"); btn.className = "ev-chat-retry"; btn.textContent = "↺ Försök igen";
        btn.onclick = function() { errDiv.remove(); chatHistory.pop(); saveChatHistory(); chatSendMessage(message, {retry:true}); };
        errDiv.appendChild(btn);
        return;
      }
    }

    bubble.innerHTML = chatMarkdown(fullText);
    evAddFeedback(outer);
    evAddFollowupChips(fullText, outer);
    msgsEl.scrollTop = msgsEl.scrollHeight;

    chatHistory.push({ role: "assistant", content: fullText });
    saveChatHistory();
  }

  window.evCalcUpdate = function evCalcUpdate() {
    if (state.carIndex === null || !state.cars.length) return;
    const car = state.cars[state.carIndex];
    const fromEl   = document.getElementById('ev-calc-from');
    const toEl     = document.getElementById('ev-calc-to');
    const resultEl = document.getElementById('ev-calc-result');
    if (!fromEl || !toEl || !resultEl) return;
    let fromPct = parseInt(fromEl.value);
    let toPct   = parseInt(toEl.value);
    if (toPct <= fromPct) { toPct = Math.min(fromPct + 10, 100); toEl.value = toPct; }
    document.getElementById('ev-calc-from-val').textContent = fromPct;
    document.getElementById('ev-calc-to-val').textContent   = toPct;

    const kwhCharge  = car.batteryKwh * (toPct - fromPct) / 100;
    const dcStation  = state.lastData?.stations.find(s => s.connectorType.includes('DC') && s.maxEffKw > 0);
    const effKw      = dcStation ? Math.min(car.maxDcKw || 50, dcStation.maxEffKw) : (car.maxDcKw || 50);
    const timeMin    = effKw > 0 ? Math.round(kwhCharge / effKw * 60) : 0;
    const timeStr    = timeMin < 60 ? `${timeMin} min` : `${Math.floor(timeMin / 60)} tim ${timeMin % 60} min`;

    const rawPrice   = dcStation?.chargepricePerKwh || dcStation?.usageCost || '';
    const isEur      = rawPrice.includes('EUR');
    const priceNum   = rawPrice.match(/[\d,.]+/)?.[0] ? parseFloat(rawPrice.match(/[\d,.]+/)[0].replace(',', '.')) : null;
    const priceKr    = priceNum ? (isEur ? priceNum * 11.5 : priceNum) : null;
    const cost       = priceKr ? Math.round(kwhCharge * priceKr) : null;

    const realRange  = car.rangeKm ? Math.round(car.rangeKm * 0.85) : null;
    const rangeAdded = realRange   ? Math.round(realRange * (toPct - fromPct) / 100) : null;

    state.lastCalc = {
      carName: car.name, fromPct, toPct, kwhCharge: Math.round(kwhCharge * 10) / 10,
      effKw: Math.round(effKw), timeStr, cost: cost || null, rangeAdded: rangeAdded || null,
      stationName: dcStation?.name || null
    };

    const cols = rangeAdded ? 3 : 2;
    resultEl.innerHTML = `<div style="display:grid;grid-template-columns:repeat(${cols},1fr);gap:10px;text-align:center;">
      <div>
        <div style="font-size:1.35rem;font-weight:800;color:#93c5fd">${timeStr}</div>
        <div style="font-size:11px;color:rgba(147,197,253,0.5);margin-top:2px">Tid</div>
      </div>
      <div>
        <div style="font-size:1.35rem;font-weight:800;color:${cost ? '#86efac' : '#6b7280'}">${cost ? '~' + cost + ' kr' : 'Pris saknas'}</div>
        <div style="font-size:11px;color:rgba(147,197,253,0.5);margin-top:2px">Kostnad</div>
      </div>
      ${rangeAdded ? `<div>
        <div style="font-size:1.35rem;font-weight:800;color:#c4b5fd">~${rangeAdded} km</div>
        <div style="font-size:11px;color:rgba(147,197,253,0.5);margin-top:2px">Räckvidd</div>
      </div>` : ''}
    </div>`;
  };

  // ===== RUTTPLANERING =====
  function initRouteMode() {
    const controls = document.querySelector('.ev-controls');
    if (!controls) return;
    const wrap = document.createElement('div');
    wrap.style.marginBottom = '16px';
    wrap.innerHTML =
      '<button id="ev-route-toggle" style="width:100%;padding:12px 16px;background:rgba(59,130,246,0.06);' +
      'border:1.5px solid rgba(59,130,246,0.2);border-radius:12px;color:rgba(147,197,253,0.8);font-size:.85rem;' +
      'font-weight:700;cursor:pointer;text-align:left;transition:all .18s;">🗺️ Planera rutt — hitta laddstoppar längs vägen</button>' +
      '<div id="ev-route-panel" class="ev-route-panel" style="display:none;">' +
        '<div class="ev-route-title">Planera laddstoppar längs rutten</div>' +
        '<div style="font-size:.78rem;color:rgba(147,197,253,.6);margin-bottom:10px;display:flex;align-items:center;gap:6px;">' +
          '<span style="font-size:.9rem">📍</span><span id="ev-route-start-label">Din position (GPS)</span>' +
        '</div>' +
        '<div class="ev-route-row">' +
          '<input class="ev-route-input" id="ev-route-end" type="text" placeholder="Destination (t.ex. Göteborg)">' +
          '<button class="ev-route-btn" id="ev-route-go">Sök</button>' +
        '</div>' +
        '<div id="ev-route-result"></div>' +
      '</div>';
    controls.after(wrap);

    const toggleBtn = document.getElementById('ev-route-toggle');
    const panel     = document.getElementById('ev-route-panel');
    toggleBtn.addEventListener('click', () => {
      const open = panel.style.display !== 'none';
      panel.style.display = open ? 'none' : 'block';
      toggleBtn.style.borderColor  = open ? 'rgba(59,130,246,0.2)' : 'rgba(59,130,246,0.5)';
      toggleBtn.style.background   = open ? 'rgba(59,130,246,0.06)' : 'rgba(59,130,246,0.1)';
      toggleBtn.style.color        = open ? 'rgba(147,197,253,0.8)' : '#93c5fd';
      if (!open && state.city) document.getElementById('ev-route-start-label').textContent = state.city;
    });
    document.getElementById('ev-route-go').addEventListener('click', planRoute);
    document.getElementById('ev-route-end').addEventListener('keydown', e => { if (e.key === 'Enter') planRoute(); });
  }

  async function geocodeCity(city) {
    const r = await fetch(
      `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(city)}&format=json&limit=1&countrycodes=se`,
      { headers: { 'User-Agent': 'EV-Laddning-App/1.0' } }
    );
    const d = await r.json();
    if (!d.length) throw new Error(`Kunde inte hitta "${city}"`);
    return { lat: parseFloat(d[0].lat), lon: parseFloat(d[0].lon), display: d[0].display_name.split(',')[0] };
  }

  async function getOsrmRoute(startLat, startLon, endLat, endLon) {
    const url = `https://router.project-osrm.org/route/v1/driving/${startLon},${startLat};${endLon},${endLat}?geometries=geojson&overview=full`;
    const r = await fetch(url, { headers: { 'User-Agent': 'EV-Laddning-App/1.0' } });
    if (!r.ok) return null;
    const d = await r.json();
    if (!d.routes?.length) return null;
    return {
      coordinates: d.routes[0].geometry.coordinates,   // [[lon,lat], ...]
      distanceKm:  Math.round(d.routes[0].distance / 100) / 10,
      durationMin: Math.round(d.routes[0].duration / 60)
    };
  }

  async function planRoute() {
    const endCity  = document.getElementById('ev-route-end')?.value?.trim();
    const resultEl = document.getElementById('ev-route-result');
    if (!resultEl) return;
    if (!endCity) {
      resultEl.innerHTML = '<p style="color:#ef4444;font-size:.82rem;margin:8px 0">Ange din destination.</p>';
      return;
    }
    if (!state.lat || !state.lon) {
      resultEl.innerHTML = '<p style="color:#ef4444;font-size:.82rem;margin:8px 0">GPS-position saknas — tillåt platsåtkomst och försök igen.</p>';
      return;
    }
    if (state.carIndex === null) {
      resultEl.innerHTML = '<p style="color:#ef4444;font-size:.82rem;margin:8px 0">Välj din elbil överst på sidan först.</p>';
      return;
    }
    const btn = document.getElementById('ev-route-go');
    btn.disabled = true; btn.textContent = 'Söker…';
    resultEl.innerHTML = '<div style="color:rgba(147,197,253,.55);font-size:.82rem;margin-top:8px">Hämtar rutt och laddstationer…</div>';
    try {
      const eg = await geocodeCity(endCity);
      const sg  = { lat: state.lat, lon: state.lon, display: state.city || 'Din position' };
      const stopsUrl = `${API}/api/route-stations?startLat=${sg.lat}&startLon=${sg.lon}&endLat=${eg.lat}&endLon=${eg.lon}&carIndex=${state.carIndex}`;

      // Fetch OSRM route + charging stops in parallel
      const [osrm, resp] = await Promise.all([
        getOsrmRoute(sg.lat, sg.lon, eg.lat, eg.lon).catch(() => null),
        fetch(stopsUrl)
      ]);
      if (!resp.ok) throw new Error('HTTP ' + resp.status);
      const data = await resp.json();
      renderRouteResult(data, sg, eg, osrm);
      collapseStationsForRoute();
      if (state.lastRoute) triggerRouteProactiveMessage();
      setTimeout(() => renderRouteMap(sg, data.stops, eg, osrm?.coordinates), 80);
    } catch (e) {
      resultEl.innerHTML = `<p style="color:#ef4444;font-size:.82rem;margin:8px 0">Fel: ${e.message}</p>`;
    } finally {
      btn.disabled = false; btn.textContent = 'Sök';
    }
  }

  function renderRouteMap(sg, stops, eg, osrmCoords) {
    if (!window.L) return;
    const mapEl = document.getElementById('ev-map');
    if (!mapEl) return;
    mapEl.style.display = 'block';

    // Clear previous route polyline and markers
    if (evRoutePolyline) { evRoutePolyline.remove(); evRoutePolyline = null; }
    evMapMarkers.forEach(m => m.remove()); evMapMarkers = [];

    const markerPoints = [
      [sg.lat, sg.lon],
      ...stops.map(s => [s.station.lat, s.station.lon]),
      [eg.lat, eg.lon]
    ];

    // Use real road geometry from OSRM if available, otherwise straight line
    const polylinePoints = osrmCoords
      ? osrmCoords.map(([lon, lat]) => [lat, lon])
      : markerPoints;

    if (!evMap) {
      evMap = L.map('ev-map', { zoomControl: true }).setView(markerPoints[0], 7);
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>', maxZoom: 19
      }).addTo(evMap);
    }
    evMap.fitBounds(L.latLngBounds(polylinePoints).pad(0.07));

    // Road route line — solid if real OSRM geometry, dashed if straight-line fallback
    evRoutePolyline = L.polyline(polylinePoints, {
      color: '#3b82f6', weight: 4, opacity: 0.75,
      dashArray: osrmCoords ? null : '8 6'
    }).addTo(evMap);

    const makeIcon = (color, label) => L.divIcon({
      className: '',
      html: `<div style="width:30px;height:30px;border-radius:50%;background:${color};border:2.5px solid #fff;display:flex;align-items:center;justify-content:center;font-size:11px;font-weight:800;color:#111;box-shadow:0 2px 8px rgba(0,0,0,0.45)">${label}</div>`,
      iconSize: [30, 30], iconAnchor: [15, 15], popupAnchor: [0, -15]
    });

    evMapMarkers.push(L.marker([sg.lat, sg.lon], { icon: makeIcon('#22c55e', 'A') }).addTo(evMap).bindPopup(`<b>Start</b><br>${sg.display}`));
    stops.forEach(stop => {
      const s = stop.station;
      const kw = Math.round(s.maxEffKw);
      evMapMarkers.push(L.marker([s.lat, s.lon], { icon: makeIcon('#f59e0b', stop.order) })
        .addTo(evMap)
        .bindPopup(`<b>${s.name}</b><br>⚡ ${kw} kW · ${s.connectorType}<br>📍 ${stop.distanceFromStartKm} km från start`));
    });
    evMapMarkers.push(L.marker([eg.lat, eg.lon], { icon: makeIcon('#ef4444', 'B') }).addTo(evMap).bindPopup(`<b>Destination</b><br>${eg.display}`));

    setTimeout(() => evMap && evMap.invalidateSize(), 100);
  }

  function renderRouteResult(data, sg, eg, osrm) {
    const resultEl = document.getElementById('ev-route-result');
    const { totalDistanceKm, stopsNeeded, carName, stops } = data;
    const displayKm   = osrm?.distanceKm ?? Math.round(totalDistanceKm);
    const driveStr    = osrm?.durationMin
      ? ` · ~${Math.floor(osrm.durationMin / 60)} tim ${osrm.durationMin % 60} min`
      : '';

    if (stopsNeeded === 0) {
      state.lastRoute = { destination: eg.display, startDisplay: sg.display, distanceKm: displayKm, durationMin: osrm?.durationMin ?? null, carName, stops: [], stopsNeeded: 0 };
      resultEl.innerHTML = `<div style="color:#86efac;font-size:.85rem;padding:10px 14px;background:rgba(34,197,94,.07);border:1px solid rgba(34,197,94,.2);border-radius:10px;margin-top:10px;">
        ✅ Din ${carName} klarar ${displayKm} km utan laddning!${driveStr ? ' Körtid' + driveStr + '.' : ''}
      </div>`;
      return;
    }
    if (!stops.length) {
      resultEl.innerHTML = `<div style="color:rgba(245,158,11,.8);font-size:.82rem;padding:10px 14px;background:rgba(245,158,11,.06);border:1px solid rgba(245,158,11,.2);border-radius:10px;margin-top:10px;">
        Inga kompatibla laddstationer hittades längs rutten. Kontrollera manuellt via Open Charge Map.
      </div>`;
      return;
    }

    state.lastRoute = { destination: eg.display, startDisplay: sg.display, distanceKm: displayKm, durationMin: osrm?.durationMin ?? null, carName, stops, stopsNeeded };

    let html = `<div style="font-size:.72rem;color:rgba(147,197,253,.5);margin:10px 0 12px;">${displayKm} km${driveStr} · ${stopsNeeded} laddning${stopsNeeded !== 1 ? 'ar' : ''} rekommenderat · ${carName}</div>`;
    html += '<div class="ev-route-timeline">';

    const dot = (cls, label) =>
      `<div class="ev-route-dot ${cls}" style="flex-shrink:0">${label}</div>`;
    const line = () =>
      `<div style="display:flex;margin-left:13px;margin-bottom:3px;"><div class="ev-route-line" style="height:18px"></div></div>`;

    html += `<div class="ev-route-stop">${dot('start','A')}<div class="ev-route-info"><div class="ev-route-name">${sg.display}</div><div class="ev-route-meta">Start · 0 km</div></div></div>`;

    stops.forEach(stop => {
      const s = stop.station;
      const kw = Math.round(s.maxEffKw);
      const price = s.chargepricePerKwh || s.usageCost || '';
      html += line();
      html += `<div class="ev-route-stop">${dot('',stop.order)}<div class="ev-route-info">
        <div class="ev-route-name">${s.name}</div>
        <div class="ev-route-meta">⚡ ${kw} kW · ${s.connectorType}${price ? ' · ' + price : ''}${s.address ? ' · ' + s.address : ''}</div>
        <div class="ev-route-meta" style="margin-top:1px">📍 ${stop.distanceFromStartKm} km från start</div>
      </div></div>`;
    });

    html += line();
    html += `<div class="ev-route-stop">${dot('end','B')}<div class="ev-route-info"><div class="ev-route-name">${eg.display}</div><div class="ev-route-meta">Destination · ${Math.round(totalDistanceKm)} km</div></div></div>`;
    html += '</div>';
    resultEl.innerHTML = html;
  }

  function collapseStationsForRoute() {
    const el = document.getElementById("ev-output");
    if (!el || !el.children.length) return;
    savedStationsHtml = el.innerHTML;
    const headerEl = el.querySelector(".ev-results-header strong");
    const label = headerEl ? headerEl.textContent : "Laddstationer nära dig";
    el.innerHTML =
      '<div id="ev-stations-minimized" style="cursor:pointer;padding:10px 16px;' +
      'background:rgba(59,130,246,0.05);border:1.5px dashed rgba(59,130,246,0.18);' +
      'border-radius:12px;color:rgba(147,197,253,0.5);font-size:.8rem;' +
      'display:flex;align-items:center;justify-content:space-between;transition:opacity .2s;">' +
      '<span>📍 ' + label + '</span>' +
      '<span style="font-size:.75rem;opacity:.65">visa ▾</span></div>';
    el.querySelector("#ev-stations-minimized").addEventListener("click", function() {
      if (savedStationsHtml) setOutput(savedStationsHtml);
    });
  }

  async function triggerRouteProactiveMessage() {
    const context = buildStationContext();
    const r = state.lastRoute;
    let trigger;
    if (r && r.stopsNeeded === 0) {
      trigger = `Jag har precis planerat en rutt till ${r.destination} (${r.distanceKm} km) och min ${r.carName} klarar sträckan utan laddningsstopp. Ge mig ett par konkreta tips inför resan — bör jag ladda fullt hemma eller vid startpunkten innan avfärd, och finns det något annat jag bör tänka på?`;
    } else if (r && r.stopsNeeded > 0) {
      trigger = `Jag har precis planerat en rutt till ${r.destination} (${r.distanceKm} km) med ${r.stopsNeeded} laddningsstopp. Ge en kort sammanfattning av rutten och laddstoppet, och om det är värt att ladda extra vid startpunkten innan avfärd.`;
    } else {
      trigger = "Jag har precis sökt en rutt. Kan du ge mig tips om resan och laddning längs vägen?";
    }
    const messages = [...chatHistory.slice(-4), { role: "user", content: trigger }];

    const fabLabel = document.querySelector(".ev-chat-fab-label");
    if (fabLabel) fabLabel.textContent = "💬 Ny info om din rutt!";

    if (!chatIsOpen()) chatSetOpen(true);
    const quickEl = document.getElementById("ev-chat-quick");
    if (quickEl) quickEl.classList.add("ev-chat-quick-off");

    let resp;
    try {
      resp = await fetch(`${API}/api/chat/stream`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ messages, context })
      });
    } catch (_) {
      if (fabLabel) fabLabel.textContent = "✨ Fråga AI";
      return;
    }
    if (!resp.ok || !resp.body) {
      if (fabLabel) fabLabel.textContent = "✨ Fråga AI";
      return;
    }

    const msgsEl = document.getElementById("ev-chat-messages");
    const outer  = document.createElement("div");
    const bubble = document.createElement("div");
    bubble.className = "ev-chat-bubble bot";
    outer.appendChild(bubble);
    msgsEl.appendChild(outer);
    msgsEl.scrollTop = msgsEl.scrollHeight;

    const reader  = resp.body.getReader();
    const decoder = new TextDecoder();
    let fullText  = "";
    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        decoder.decode(value, { stream: true }).split("\n").forEach(line => {
          if (!line.startsWith("data: ")) return;
          const d = line.slice(6).trim();
          if (d === "[DONE]") return;
          try { fullText += JSON.parse(d); bubble.innerHTML = chatMarkdown(fullText); msgsEl.scrollTop = msgsEl.scrollHeight; } catch(e) {}
        });
      }
    } catch(_) {}

    if (fullText) { chatHistory.push({ role: "assistant", content: fullText }); saveChatHistory(); }
    if (fabLabel) setTimeout(() => { fabLabel.textContent = "✨ Fråga AI"; }, 10000);
  }

  initChat();
  initRouteMode();
})();
