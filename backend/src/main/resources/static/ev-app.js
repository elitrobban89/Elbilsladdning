(function () {
  const API = window.EV_API_URL || "https://elbilsladdning.onrender.com";
  let state = { lat: null, lon: null, city: "", sort: "speed", carIndex: null, cars: [], filter: "all", lastData: null, favorites: [] };

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
        o.value = i; o.textContent = `${c.name}  (AC ${c.maxAcKw} kW · DC ${c.maxDcKw} kW)`;
        sel.appendChild(o);
      });
    })
    .catch(() => {
      document.getElementById("ev-car-select").innerHTML = "<option>Kunde inte hämta bilar</option>";
    });

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

    const visible = state.filter === "fast"
        ? stations.filter(s => s.connectorType.includes("DC") && s.maxEffKw >= 50)
        : stations;
    const top = visible.slice(0, 10);
    let html = "";

    const fastDC = stations
      .filter(s => s.maxEffKw >= 50 && s.connectorType.includes("DC"))
      .sort((a, b) => a.distanceKm - b.distanceKm)[0];

    if (fastDC) {
      const url  = operatorUrl(fastDC.operator);
      const dist = fastDC.distanceKm.toFixed(1);
      const kw   = Math.round(fastDC.maxEffKw);
      const nm   = fastDC.name.length > 42 ? fastDC.name.slice(0,40)+"…" : fastDC.name;
      const pris = fastDC.chargepricePerKwh || "";
      html += `
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

    if (data.funFact) {
      html += `
        <div class="ev-funfact-card">
          <div class="ev-funfact-icon">💡</div>
          <div>
            <div class="ev-funfact-label">Visste du att</div>
            <div class="ev-funfact-text">${data.funFact}</div>
          </div>
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

      const mode = modes[Math.floor(Math.random() * modes.length)];
      const { icon, label, colHeader, data, formatVal, factFn } = mode;
      const factText = factFn(data[0]);
      const myRank = data.findIndex(c => c.name === selectedName) + 1;

      const isWltp = icon === '🎯';

      const buildRow = (c, rank, hl, stripe) => {
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

      const rows = data.map((c, i) => buildRow(c, i + 1, c.name === selectedName, i % 2 === 1)).join('');

      const th1 = isWltp ? 'WLTP'    : colHeader;
      const th2 = isWltp ? '~Verklig' : 'Pris';

      html += `
        <div class="ev-funfact-card">
          <div class="ev-funfact-icon">${icon}</div>
          <div style="flex:1">
            <div class="ev-funfact-label">${label}</div>
            <div class="ev-funfact-text" style="margin-bottom:10px;">${factText}</div>
            <div style="border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;margin-top:4px;">
              <div style="overflow-y:auto;max-height:260px;">
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

    const filterNote = state.filter === "fast" ? " · DC ≥50 kW" : "";
    html += `
      <div class="ev-results-header">
        <strong>${stations.length} kompatibla stationer inom 25 km</strong>
        <span>Topp ${top.length}${filterNote}</span>
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

      html += `
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
    });

    setOutput(html);
  }

  function setOutput(html) {
    document.getElementById("ev-output").innerHTML = html;

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
  }

  // ===== CHATTBOT =====
  const chatHistory = [];

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
      .ev-chat-fab {
        width:52px;height:52px;border-radius:50%;
        background:radial-gradient(circle at 35% 35%,#fde68a,#f59e0b 60%,#d97706);
        color:#78350f;font-size:22px;border:none;cursor:pointer;
        box-shadow:0 2px 12px rgba(245,158,11,.4),0 0 0 0 rgba(245,158,11,.3);
        display:flex;align-items:center;justify-content:center;
        transition:transform .15s,box-shadow .15s;
        animation:ev-fab-glow 4s ease-in-out infinite;
      }
      @keyframes ev-fab-glow {
        0%,100%{box-shadow:0 2px 12px rgba(245,158,11,.4),0 0 0 0 rgba(245,158,11,.25)}
        50%{box-shadow:0 4px 18px rgba(245,158,11,.55),0 0 0 8px rgba(245,158,11,.06)}
      }
      .ev-chat-fab:hover { transform:scale(1.08);box-shadow:0 6px 20px rgba(245,158,11,.6); }
      .ev-chat-panel {
        position:fixed;bottom:92px;right:24px;z-index:9998;
        width:340px;max-height:520px;
        background:#fff;border-radius:16px;
        box-shadow:0 8px 32px rgba(0,0,0,.18);
        display:flex;flex-direction:column;overflow:hidden;
        font-family:inherit;
      }
      .ev-chat-header {
        background:linear-gradient(135deg,#1e3a8a,#1d4ed8);
        color:#fff;padding:14px 16px;
        display:flex;align-items:center;justify-content:space-between;
        font-weight:700;font-size:14px;flex-shrink:0;
      }
      .ev-chat-header-close {
        background:none;border:none;color:#fff;font-size:20px;
        cursor:pointer;padding:0 2px;line-height:1;opacity:.8;
      }
      .ev-chat-header-close:hover { opacity:1; }
      .ev-chat-messages {
        flex:1;overflow-y:auto;padding:14px 12px;
        display:flex;flex-direction:column;gap:10px;
        background:#f8fafc;min-height:0;
      }
      .ev-chat-bubble {
        max-width:85%;padding:10px 13px;border-radius:14px;
        font-size:13px;line-height:1.5;word-break:break-word;
      }
      .ev-chat-bubble.bot {
        background:#fff;border:1px solid #e5e7eb;
        border-radius:4px 14px 14px 14px;align-self:flex-start;color:#111827;
      }
      .ev-chat-bubble.user {
        background:linear-gradient(135deg,#1d4ed8,#3b82f6);
        color:#fff;border-radius:14px 14px 4px 14px;align-self:flex-end;
      }
      .ev-chat-quick {
        padding:10px 12px 4px;display:flex;flex-wrap:wrap;gap:7px;flex-shrink:0;
        background:#f8fafc;border-top:1px solid #e5e7eb;
      }
      .ev-chat-quick-btn {
        background:#fff;border:1.5px solid #3b82f6;color:#1d4ed8;
        border-radius:20px;padding:5px 12px;font-size:12px;font-weight:600;
        cursor:pointer;transition:background .12s,color .12s;white-space:nowrap;
      }
      .ev-chat-quick-btn:hover { background:#3b82f6;color:#fff; }
      .ev-chat-input-row {
        display:flex;gap:8px;padding:10px 12px;
        border-top:1px solid #e5e7eb;background:#fff;flex-shrink:0;
      }
      .ev-chat-input {
        flex:1;border:1.5px solid #d1d5db;border-radius:22px;
        padding:8px 14px;font-size:13px;outline:none;
        transition:border-color .12s;
      }
      .ev-chat-input:focus { border-color:#3b82f6; }
      .ev-chat-send {
        width:38px;height:38px;border-radius:50%;
        background:#1d4ed8;color:#fff;border:none;cursor:pointer;
        font-size:16px;display:flex;align-items:center;justify-content:center;
        flex-shrink:0;transition:background .12s;
      }
      .ev-chat-send:hover { background:#2563eb; }
      .ev-chat-typing { display:flex;gap:4px;align-items:center;padding:4px 0; }
      .ev-chat-typing span {
        width:7px;height:7px;border-radius:50%;background:#9ca3af;
        animation:ev-bounce .9s infinite;display:inline-block;
      }
      .ev-chat-typing span:nth-child(2) { animation-delay:.15s; }
      .ev-chat-typing span:nth-child(3) { animation-delay:.3s; }
      @keyframes ev-bounce { 0%,80%,100%{transform:translateY(0)} 40%{transform:translateY(-6px)} }
      @media(max-width:400px){
        .ev-chat-panel{width:calc(100vw - 16px);right:8px;bottom:88px;}
        .ev-chat-fab-wrap{right:12px;bottom:12px;}
      }
    `;
    document.head.appendChild(style);

    const root = document.createElement("div");
    root.innerHTML = `
      <div class="ev-chat-fab-wrap">
        <span class="ev-chat-fab-label">✨ Fråga AI</span>
        <button class="ev-chat-fab" id="ev-chat-fab" title="Fråga EV-assistenten">☀️</button>
      </div>
      <div class="ev-chat-panel" id="ev-chat-panel" style="display:none;">
        <div class="ev-chat-header">
          <span>⚡ EV-Assistenten</span>
          <button class="ev-chat-header-close" id="ev-chat-close">✕</button>
        </div>
        <div class="ev-chat-messages" id="ev-chat-messages"></div>
        <div class="ev-chat-quick" id="ev-chat-quick">
          <button class="ev-chat-quick-btn" data-q="Ge mig råd för att köpa elbil">🚗 Elbilsköp</button>
          <button class="ev-chat-quick-btn" data-q="Vilken elbil laddar snabbast med DC?">⚡ Snabbaste DC</button>
          <button class="ev-chat-quick-btn" data-q="Vilken elbil har längst räckvidd?">🛣️ Räckvidd</button>
          <button class="ev-chat-quick-btn" data-q="Vilken elbil ger bäst värde för pengarna?">💰 Bäst värde</button>
        </div>
        <div class="ev-chat-input-row">
          <input class="ev-chat-input" id="ev-chat-input" type="text" placeholder="Ställ en fråga…" autocomplete="off" />
          <button class="ev-chat-send" id="ev-chat-send">➤</button>
        </div>
      </div>
    `;
    document.body.appendChild(root);

    chatAppendBot("Undrar du vilken elbil du bör köpa? 🚗 Jag kan ge dig tips! Välj ett ämne nedan eller ställ en egen fråga.");

    document.getElementById("ev-chat-fab").addEventListener("click", chatToggle);
    document.getElementById("ev-chat-close").addEventListener("click", chatToggle);
    document.getElementById("ev-chat-send").addEventListener("click", chatSend);
    document.getElementById("ev-chat-input").addEventListener("keydown", e => { if (e.key === "Enter") chatSend(); });
    document.querySelectorAll(".ev-chat-quick-btn").forEach(btn =>
      btn.addEventListener("click", () => chatSendMessage(btn.dataset.q))
    );
  }

  function chatToggle() {
    const panel = document.getElementById("ev-chat-panel");
    const open = panel.style.display === "none";
    panel.style.display = open ? "flex" : "none";
    if (open) document.getElementById("ev-chat-input").focus();
  }

  function chatAppendBot(text) {
    const msgs = document.getElementById("ev-chat-messages");
    const div = document.createElement("div");
    div.innerHTML = `<div class="ev-chat-bubble bot">${text}</div>`;
    msgs.appendChild(div);
    msgs.scrollTop = msgs.scrollHeight;
    return div;
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

  async function chatSendMessage(message) {
    chatAppendUser(message);
    chatHistory.push({ role: "user", content: message });

    const msgs = document.getElementById("ev-chat-messages");
    const typingDiv = document.createElement("div");
    typingDiv.innerHTML = `<div class="ev-chat-bubble bot"><div class="ev-chat-typing"><span></span><span></span><span></span></div></div>`;
    msgs.appendChild(typingDiv);
    msgs.scrollTop = msgs.scrollHeight;

    try {
      const resp = await fetch(`${API}/api/chat`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ messages: chatHistory })
      });
      const data = await resp.json();
      typingDiv.remove();
      chatHistory.push({ role: "assistant", content: data.reply });
      chatAppendBot(data.reply);
    } catch (_) {
      typingDiv.remove();
      chatAppendBot("Kunde inte nå assistenten just nu – försök igen om en stund.");
    }
  }

  initChat();
})();
