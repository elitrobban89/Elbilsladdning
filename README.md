# EV Laddningsassistent ⚡

[![Build & Test](https://github.com/elitrobban89/Elbilsladdning/actions/workflows/maven.yml/badge.svg)](https://github.com/elitrobban89/Elbilsladdning/actions/workflows/maven.yml)

En webbaserad laddningsassistent för elbilar i Sverige. Hitta kompatibla laddstationer nära dig, sorterade efter hastighet eller pris.

Live: [elitrobban.se/elbilsladdning](https://elitrobban.se/elbilsladdning/)

> **Åtkomst kräver prenumeration** — tjänsten är bakom en betalvägg (`ev-charging.js` från CarAdvice-backenden). En prenumeration på **49 kr/mån** via [elitrobban.se/bilradgivning](https://elitrobban.se/bilradgivning/) ger tillgång till båda tjänsterna med samma konto.

---

## Funktioner

- **GPS-baserad sökning** — hittar laddstationer inom 15 km automatiskt
- **73 bilmodeller** — Volvo, Tesla, BMW, Audi, Kia, Hyundai, MG, BYD m.fl. — filtrerar på kontakttyp och laddeffekt
- **Top 5 stationer** — visar de 5 bästa kompatibla stationerna inom 15 km; sortera på DC-effekt eller pris per kWh
- **Filtrera laddtyp** — visa endast snabbladdare DC (≥50 kW) med ett klick
- **Operatörsfilter** — klickbara chips ovanför stationslistan med unika operatörsnamn (Recharge, IONITY, Easypark osv.); filtret återställs automatiskt vid ny sökning
- **Räckvidd** — WLTP-räckvidd och uppskattad verklig räckvidd (~85 % av WLTP)
- **Laddtidsestimering** — ungefärlig tid för 20→80 % per DC-station baserat på din bil och stationens kW
- **Kostnadskalkyl** — kostnad för full laddning och kr/mil vid varje station
- **Hemjämförelse** — hur mycket dyrare det är att ladda på stationen vs hemma (~2 kr/kWh)
- **Laddningsfrekvens** — ange årskörsträcka och se hur ofta du behöver ladda
- **Favoritstationer** — spara och hantera favoritstationer, lagras i PostgreSQL per webbläsare (anonymt UUID)
- **AI-rekommendation** — Groq LLM (`openai/gpt-oss-20b`, `reasoning_effort: low` så reasoning inte äter tokenbudgeten) ger ett konkret råd per sökning, märkt med ⚡ GROQ-badge; 30 min cache per bil+stationskombination sparar tokens och ger snabbare svar
- **Groq 429-fallback** — vid dagsgräns returneras regelbaserat svar (bästa stationen med km/kW) direkt utan AI-anrop; `quotaExceededUntil`-backoff nollställs automatiskt vid nästa lyckade anrop; chat-endpointen kontrollerar samma backoff-flagga
- **Rekommendations-cache** — 30 min TTL per bil+stationskombination; rensas automatiskt vid >200 entries för att hålla minnesanvändningen i schack
- **IP-begränsning på stationssök** — max 10 förfrågningar per timme och IP (sliding window), 429 med svensk feltext vid överskridning; IP-poster rensas i schemalagd task varje timme
- **Parallell exekvering** — OCM och NOBIL körs samtidigt; prisberikning för alla 5 stationer körs parallellt via Java 21 virtuella trådar (`newVirtualThreadPerTaskExecutor`); sparar ~1–2 s per sökning vid cold cache
- **"Visste du att"-karusell** — roterande kortlek med AI-genererat bilfakta, en automatiskt uppdaterad topplista-fakta (se nedan) + 11 statiska fakta (Volvo EX40 mest sålda elbilen 2025 med 8 788 nyregistreringar och ledare även H1 2026, IONITY 350 kW i Norden, Vattenfall InCharge 33 000+ punkter, vinterpåverkan 20–40 %, Tesla Supercharger öppet sedan 2023, Mobility Swedens prognos 45 % elbilsandel 2026, Mercedes CLA Årets Bil 2026, Volvo 16,5 % marknadsandel juni 2026, VW ID.4 månadsetta bland renodlade elbilar maj 2026 med 687 registreringar tätt före Model Y och Polestar 2, EV6/ID.3/Enyaq som rundar av majitoppen, elbilar gick om laddhybrider kring årsskiftet 2025/2026 och passerade diesel i april 2026 — de tre senaste källa Carla.se elbilsindex/statistik, uppdateras manuellt månadsvis då sajten blockerar automatisk skrapning); auto-roterar var 9:e sekund med mjuk crossfade-animation (slide glider upp + tonar in); klickbara punkter för manuell navigering
- **Automatisk elbilstopplista** — `EvSalesRankSyncService` hämtar den 6:e varje månad 05:30 Stockholm (`EvSalesRankSyncScheduler`) elbilsvaruhuset.se:s inlägg "De 10 populäraste elbilarna i Sverige" via öppet WordPress REST-API (`wp-json/wp/v2/posts`, ingen bot-spärr till skillnad från carla.se) — källa Mobility Sweden, renodlat BEV (ingen laddhybrid-/bensinblandning). Tabellen (rank/modell/antal/pris/period) parsas ur riktiga HTML-`<table>`-celler, inte plattad text — modellnamn med siffror ("Polestar 4", "ID.7", "bZ4X") skulle annars blandas ihop med antalskolumnen. Ersätter hela tabellen varje körning (`ev_sales_rank`, ingen historik). Publikt `GET /api/ev-sales-rank`; manuell admin-trigger `POST /api/admin/sync-ev-sales-rank` (X-Admin-Key). Frontend bygger en dynamisk "Visste du att"-fakta av ettan/tvåan varje sidladdning — uppdateras alltså av sig själv utan kodändring.
- **Roterande faktatabeller** — alla fyra tabeller cyklar som en karusell under en tydlig "💡 Visste du att?"-avgränsare: värde för pengarna (km/100 tkr), snabbast DC-laddning, längst räckvidd och WLTP vs verklig räckvidd; föregående/nästa-pilar + punktnavigering + auto-rotate var 9:e sekund; crossfade-transition mellan slides; alla 73 bilar scrollbara med sticky header; vald bil highlightad i blått och auto-scrollad till vid slide-byte; auto-scroll scrollar bara inne i tabellen (inte hela sidan) via `scrollTop` på tabellens scroll-container
- **Livepriser** — Chargeprice API + statisk operatörstabell täcker de flesta svenska nätverk
- **NOBIL-integration** — hämtar antal laddpunkter per station (aktiveras med API-nyckel)
- **Interaktiv karta** — Leaflet + OpenStreetMap (gratis, ingen API-nyckel, serveras lokalt utan CDN-beroende) visas ovanför stationslistan; färgkodade markörer: 🟢 grön ≥100 kW, 🟠 orange ≥22 kW, 🟣 lila långsam; din position som blå cirkel; klicka markör för popup med kW, kontakttyp, pris och avstånd; zoomnivå 17
- **Ruttplanering** — kollapsbar panel "Planera rutt" under kontrollerna; GPS-position används automatiskt som start; ange bara destination (t.ex. "Göteborg"); backend beräknar antal laddstoppar (75 % av WLTP per etapp) och söker närmaste kompatibla station per hållplats via OCM; kartan visar hela rutten som en verklig väglinje via [OSRM](https://project-osrm.org/) med faktisk motorvägsgeometri (E4, E6 osv.), gul A-markör vid start, numrerade gula markörer vid laddstoppar, röd B-markör vid mål; rubrikraden visar vägavstånd och uppskattad körtid; faller tillbaka på streckad rak linje om OSRM är otillgänglig; OSRM och laddstationssökning körs parallellt — ingen extra väntetid
- **Stationer kollapsar vid ruttplanering** — när ruttresultat visas minimeras den lokala stationslistan automatiskt till en klickbar rad ("📍 X stationer nära dig · visa ▾"); kartan och ruttresultatet tar fokus; klick på raden återställer stationslistan
- **Proaktiv ruttchatt** — chatboten öppnas automatiskt och sammanfattar rutten och laddstoppet direkt när ruttplanering slutförts, utan att användaren behöver fråga; svaret streamar token för token och sparas i chatthistoriken
- **AI-chattbot** ⚡ — flytande chattassistent (knapp nere till höger med animerade blixtar) driven av Groq; polerad glassmorphism-design med öppningsanimation, grön pulserande online-indikator och underrubrik "AI • Laddning & Elbilar"; stödjer markdown i bot-svar (fetstil, listor); rensa-knapp i headern; svarar ENDAST på frågor om elbilar, laddning, räckvidd och stationer; smarta budgetregler (budget under 200 tkr → föreslår begagnade med prisintervall, rekommenderar aldrig ny bil >1,3x budgeten); stödjer flerturskonversation; max 10 frågor/minut per IP
- **Streaming-svar** — chattbotens svar strömmar direkt token för token via `/api/chat/stream` (SSE) utan att vänta på hela svaret; automatisk fallback till vanlig JSON-endpoint om webbläsaren saknar ReadableStream-stöd
- **Dynamiska follow-up chips** — efter varje svar visas 2–3 kontextuella snabbknappar baserade på svarsinnehållet (räckvidd, laddning, pris, bilmodeller)
- **Full appkontext till chattboten** — chatboten får automatiskt hela skärmens kontext: vald bil (batteri, DC, räckvidd, pris), laddtidskalkylatorn (från/till%, tid, kostnad, tillkommen räckvidd), planerad rutt (destination, alla laddstoppar med avstånd och pris), DC-ranking topp 3, räckvidsranking topp 3, AI-rekommendation och faktaruta visad på skärmen; möjliggör naturliga följdfrågor på allt som visas
- **Prenumerationsinfo** — chatboten svarar korrekt på frågor om prenumerationen: 49 kr/mån inkluderar AI Bilrådgivning, AI EV Laddassistent och Bränslekostnadsberäkning
- **Billigaste laddning** — vid fråga om billigaste laddning rekommenderas alltid hemmaladdning (~1,50–3,50 kr/kWh) först, följt av billigaste publik station i listan med namn, pris och avstånd
- **Ruttkontext** — om en rutt är planerad kan chattboten förklara varför ett specifikt laddstop valts, om bilen klarar sträckan utan stopp, och hur stopparna är utplacerade längs vägen
- **Laddtidskalkylator** — interaktiv kalkylator visas under sökresultaten: dra sliders "Ladda från X% till Y%" och se beräknad tid, kostnad och tillkommen räckvidd i realtid; använder vald bils batteristorlek och den närmaste DC-stationens effektiva kW
- **Mobilanpassad** — fungerar på iOS och Android
- **PWA-stöd** — `manifest.json` gör appen installerbar på Android/iOS via "Lägg till på startskärm"

---

## Teknikstack

| Del | Teknologi |
|-----|-----------|
| Backend | Spring Boot 3.2.5 / Java 21 |
| Hosting backend | Render (free tier, Docker) |
| Stationsdata | [Open Charge Map API](https://openchargemap.io) |
| Laddpunkter | [NOBIL API](https://info.nobil.no/api) — nordisk databas, ger antal kontakter per station |
| Livepriser | [Chargeprice API](https://chargeprice.app) (demo-nyckel) |
| AI | Groq (`openai/gpt-oss-20b`) |
| Karta | [Leaflet](https://leafletjs.com) + [OpenStreetMap](https://www.openstreetmap.org) — gratis, ingen API-nyckel |
| Frontend | Vanilla JS + CSS, inbäddat i WordPress |
| JS-hosting | Render static file (`/ev-app.js`) — serveras separat från WordPress |

---

## Tester & CI

73 tester i tre lager — ren logik, HTTP-felvägar och controller-lagret (MockMvc, tjänsterna mockas):

| Testklass | Täcker |
|-----------|--------|
| `RouteServiceTest` (9) | Ruttmatte: haversine, stopp utifrån 75 % av räckvidden, stationsval per stopp |
| `CarSpecServiceTest` (8) | Entitetsmappning, dubblettdedup, kontakttyper (CCS/CHAdeMO), DB-fel → hårdkodad fallback, cache |
| `OperatorPriceServiceTest` (12) | Operatörsmatchning, stationsnamnsfallback, OCM-placeholder ignoreras |
| `GroqServiceTest` (8) | Promptbygget (topplistor, stationslista, kostnadsjämförelse), fallbacksvar, 429-retry-parsning, sektionsextraktion |
| `GroqServiceHttpTest` (6) | HTTP-felvägar mot lokal stubbserver: 429 sätter kvotspärr + kortsluter nästa anrop, trasigt JSON ger fallback, sektionsparsning av lyckat svar |
| `EvSalesRankSyncServiceParseTest` (7) | Parsning av elbilsvaruhuset.se:s rankingtabell ur riktiga table/td-celler — modellnamn med siffror ("Polestar 4", "ID.7", "bZ4X") blandas inte ihop med antalskolumnen |
| `EvSalesRankSyncServiceHttpTest` (4) | Full synk mot lokal stubbserver: lyckad parsning+spara, tomt svar/saknad tabell/HTTP-fel ger ERROR utan att röra befintliga rader |
| `ChargingControllerTest` (10) | Billistans form, bilindexvalidering 400, prisberikning i stationsflödet, chattens rate limit → 429 |
| `FavoriteControllerTest` (5) | Dubblettskydd 409, ägarkontroll vid borttagning 404, spara/lista/ta bort |
| `RouteControllerTest` (4) | Bilindexvalidering 400, ruttplanens JSON-form |

```bash
cd backend
mvn test
```

GitHub Actions ([maven.yml](.github/workflows/maven.yml)) kör testerna på varje push — badgen överst visar status.

---

## Prissättning

Laddpriser hämtas från tre källor i prioritetsordning:

| Källa | Prioritet | Beskrivning |
|-------|-----------|-------------|
| **Chargeprice — OCM-adapter** | 1 | Slår upp exakt station via OCM-ID (`data_adapter: "open_charge_map"`); mest träffsäkert |
| **Chargeprice — nätverksnamn** | 2 | Matchar operatörsnamn mot Chargeprice Going Electric-databas; fallback om OCM-ID ger inget |
| **OCM UsageCost-fält** | 3 | Finns ibland i Open Charge Map-data |
| **Statisk operatörstabell** | 4 | Priser utan abonnemang, uppdaterade 2026-06-13 |

Statisk tabell (utan abonnemang, avrundade): Recharge ~3,49, InCharge ~3,49, Circle K ~5,99, MER ~6,24, IONITY ~6,96, E.ON ~4,75, Allego ~6,50, Bee ~3,29 kr/kWh m.fl.
Priser markerade med `~` är ungefärliga — verifiera alltid hos respektive operatör.

---

## Projektstruktur

```
backend/                         Spring Boot-backend (Render)
  src/main/java/se/elitrobban/elbilsladdning/
    config/
      WebConfig.java                     Global CORS-konfiguration för /api/**
    controller/
      ChargingController.java            REST-endpoints /api/cars, /api/stations och /api/charging-price
      FavoriteController.java            CRUD /api/favorites — GET, POST, DELETE
      RouteController.java               GET /api/route-stations — ruttplanering med laddstoppar
      EvSalesRankController.java         GET /api/ev-sales-rank (publik) + POST /api/admin/sync-ev-sales-rank (X-Admin-Key)
    data/CarDatabase.java                73 bilmodeller med AC/DC-effekt, batteri, räckvidd och pris
    model/
      CarSpec.java                       Record — bilspecifikationer
      StationDto.java                    Record — laddstation med priser, laddpunktsantal och OCM-ID
      StationResponse.java               Record — API-svar med stationer, AI-råd, funfact och carFact (värderanking)
      RouteStop.java                     Record — ett laddstop i en rutt (ordning, km från start, station)
      RouteResponse.java                 Record — ruttplaneringssvar (vägavstånd, antal stopp, laddstoppar)
      FavoriteStation.java               JPA-entity — sparade favoritstationer (ev_favorites)
      EvSalesRankEntry.java              JPA-entity — senaste elbilstopplistan (ev_sales_rank), ersätts varje synk
    repository/
      FavoriteStationRepository.java     Spring Data JPA — findByUserId, existsByUserIdAndName
      EvSalesRankRepository.java         Spring Data JPA — findAllByOrderByRankAsc
    scraper/
      EvSalesRankSyncService.java        Hämtar + parsar elbilsvaruhuset.se:s topplista (WordPress REST-API)
      EvSalesRankSyncScheduler.java      Månadsvis synk — den 6:e 05:30 Stockholm
    service/
      OcmService.java                    Hämtar stationer från Open Charge Map
      NobilService.java                  Hämtar laddpunktsdata från NOBIL (nordisk databas)
      ChargepriceService.java            Livepriser via Chargeprice API
      OperatorPriceService.java          Statisk prislista för svenska operatörer (fallback)
      GroqService.java                   AI-rekommendation, "Visste du att" och chattbot via Groq
      RouteService.java                  Beräknar laddstoppar längs rutt med haversine + OCM-sökning per etapp
      ApiNinjasService.java              API Ninjas-integration (reserv)

elbilsladdning-web.html                  WordPress-snippet — HTML + CSS + script-taggar; innehåller ev-sub-bar och ev-content (display:none) som ev-charging.js kontrollerar
  src/main/resources/static/
    ev-app.js                            All frontend-logik — serveras av Render, aldrig av WordPress
    test.html                            Lokal testmiljö — öppnas via http://localhost:8080/test.html
```

> **OBS:** JavaScript ligger i `ev-app.js` och serveras av Render som en statisk fil.
> WordPress-snippeten innehåller inget inline-JS — WordPress HTML-encodar `&&` och andra operatorer
> i inline scripts vilket bryter JavaScript-syntaxen.

---

## Köra lokalt

```bash
cd backend
OCM_API_KEY=xxx GROQ_API_KEY=xxx mvn spring-boot:run
```

Obligatoriska miljövariabler: `OCM_API_KEY`, `GROQ_API_KEY`.  
Valfria: `NOBIL_API_KEY` (aktiverar laddpunktsantal per station), `CHARGEPRICE_API_KEY`, `APININJAS_API_KEY`.  
Se `application.properties` för fullständig lista.

Öppna sedan `http://localhost:8080/test.html` i webbläsaren — samma app som på live-siten men mot lokal backend.

### Chattbotten lokalt

`POST /api/chat` accepterar en konversationshistorik och returnerar ett AI-svar (JSON):

```json
POST http://localhost:8080/api/chat
{ "messages": [{ "role": "user", "content": "Vilken elbil laddar snabbast?" }] }
```

`POST /api/chat/stream` returnerar samma svar som SSE (token för token):

```
POST http://localhost:8080/api/chat/stream
→ Content-Type: text/event-stream
data: "Hyundai"
data: " IONIQ"
data: " 6"
...
data: [DONE]
```

### Ruttplanering för externa konsumenter

`GET /api/route-stations` fungerar nu även **utan `carIndex`** — externa konsumenter (Bilresas
kalkylator visar laddstopp för elresor över 25 mil) skickar `rangeKm` i stället (klampas till
100–800, default 400) och får en generisk elbil med alla kontakttyper. Stoppen prisberikas ur
operatörstabellen (`OperatorPriceService`) när OCM saknar pris.

```
GET /api/route-stations?startLat=57.71&startLon=11.97&endLat=59.33&endLon=18.07&rangeKm=400
```

### Snabbladdarpris för externa konsumenter

`GET /api/charging-price?lat=57.71&lon=11.97` — konsumeras av **Bilresas bränslekostnadskalkylator**
(⚡ Snabbladdare-chippen). Med koordinater: priset hos närmaste DC-station vars operatör finns i
`OperatorPriceService`-tabellen. Utan koordinater eller utan träff: riksgenomsnittet av tabellen
(aliasnycklar räknas en gång, gratisladdning exkluderas). Rate limit 30 anrop/h per IP.

```json
{ "source": "nearest-station", "priceKr": 5.99, "priceLabel": "~5,99 kr/kWh",
  "station": "Circle K Backaplan", "operator": "Circle K", "distanceKm": 1.4,
  "maxKw": 150, "avgNationalKr": 4.72 }
```

```json
{ "source": "national-average", "priceKr": 4.72, "avgNationalKr": 4.72 }
```

---

## Miljövariabler (Render)

| Variabel | Krävs | Beskrivning |
|----------|-------|-------------|
| `OCM_API_KEY` | ✅ | Open Charge Map API-nyckel |
| `GROQ_API_KEY` | ✅ | Groq API-nyckel för AI-rekommendationer |
| `DB_URL` | ✅ | JDBC-URL till PostgreSQL, t.ex. `jdbc:postgresql://host/db` |
| `DB_USER` | ✅ | PostgreSQL-användare |
| `DB_PASS` | ✅ | PostgreSQL-lösenord |
| `CHARGEPRICE_API_KEY` | ⚪ | Chargeprice API-nyckel (demo-nyckel fungerar) |
| `APININJAS_API_KEY` | ⚪ | API Ninjas (valfri reservkälla) |
| `NOBIL_API_KEY` | ⚪ | NOBIL API-nyckel — aktiverar antal laddpunkter per station (nordisk databas, aktivt konfigurerat) |
| `ADMIN_KEY` | ⚪ | Nyckel för `X-Admin-Key`-skyddade admin-endpoints (t.ex. `POST /api/admin/sync-ev-sales-rank`); saknas den nekas admin-anrop |
