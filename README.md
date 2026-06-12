# EV Laddningsassistent ⚡

En webbaserad laddningsassistent för elbilar i Sverige. Hitta kompatibla laddstationer nära dig, sorterade efter hastighet eller pris.

Live: [elitrobban.se/elbilsladdning](https://elitrobban.se/elbilsladdning/)

---

## Funktioner

- **GPS-baserad sökning** — hittar laddstationer inom 25 km automatiskt
- **73 bilmodeller** — filtrerar stationer baserat på din bils kontakttyp och laddeffekt
- **Snabbast / Billigast** — sortera på DC-effekt eller pris per kWh
- **Räckvidd** — visar WLTP-räckvidd och uppskattad verklig räckvidd (~85 % av WLTP)
- **Kostnadskalkyl** — ungefärlig kostnad för 0→100 % samt kr/mil (baserat på verklig räckvidd)
- **Laddningsfrekvens** — ange årskörsträcka och se hur ofta du behöver ladda (20→80 %, verklig räckvidd)
- **AI-rekommendation** — Groq LLM ger ett kort råd baserat på din bil och tillgängliga stationer
- **Hemmaladdningstips** — påminner om att hemmaladdning (1,50–3,50 kr/kWh) alltid är billigast
- **Mobilanpassad** — fungerar på iOS och Android

---

## Teknikstack

| Del | Teknologi |
|-----|-----------|
| Backend | Spring Boot 3.2.5 / Java 21 |
| Hosting backend | Render (free tier, Docker) |
| Stationsdata | [Open Charge Map API](https://openchargemap.io) |
| Livepriser | [Chargeprice API](https://chargeprice.app) (demo-nyckel) |
| AI | Groq (llama-3.3-70b-versatile) |
| Frontend | Vanilla JS + CSS, inbäddat i WordPress |

---

## Prissättning

Laddpriser hämtas från tre källor i prioritetsordning:

| Källa | Status | Beskrivning |
|-------|--------|-------------|
| **Chargeprice.app** | ✅ Aktiv | Demo-API-nyckel, täcker stora operatörer (IONITY, Recharge m.fl.) |
| **OCM UsageCost-fält** | ✅ Används | Finns ibland i Open Charge Map-data |
| **Statisk operatörstabell** | ✅ Fallback | Manuellt insamlade priser för svenska operatörer |

Den statiska tabellen täcker Recharge, IONITY, InCharge, Circle K, E.ON, Mer, Allego m.fl.
Priser markerade med `~` är ungefärliga — verifiera alltid hos respektive operatör.

---

## Projektstruktur

```
backend/                         Spring Boot-backend (Render)
  src/main/java/se/elitrobban/elbilsladdning/
    controller/ChargingController.java   REST-endpoints /api/cars och /api/stations
    data/CarDatabase.java                73 bilmodeller med AC/DC-effekt, batteri och WLTP-räckvidd
    model/                               CarSpec, StationDto, StationResponse
    service/
      OcmService.java                    Hämtar stationer från Open Charge Map
      ChargepriceService.java            Livepriser via Chargeprice API
      OperatorPriceService.java          Statisk prislista för svenska operatörer (fallback)
      GroqService.java                   AI-rekommendation via Groq
      ApiNinjasService.java              API Ninjas-integration (reserv)

elbilsladdning-web.html                  Frontend — inbäddas i WordPress
src/                                     Konsolapplikation (utvecklingsverktyg)
```

---

## Köra lokalt

```bash
cd backend
./mvnw spring-boot:run
```

Kräver miljövariablerna `OCM_API_KEY` och `GROQ_API_KEY`. Se `application.properties`.

---

## Miljövariabler (Render)

| Variabel | Beskrivning |
|----------|-------------|
| `OCM_API_KEY` | Open Charge Map API-nyckel |
| `GROQ_API_KEY` | Groq API-nyckel för AI-rekommendationer |
| `CHARGEPRICE_API_KEY` | Chargeprice API-nyckel (demo-nyckel fungerar) |
| `APININJAS_API_KEY` | API Ninjas (valfri reservkälla) |
