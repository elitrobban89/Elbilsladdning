# EV Laddningsassistent ⚡

En webbaserad laddningsassistent för elbilar i Sverige. Hitta kompatibla laddstationer nära dig, sorterade efter hastighet eller pris.

Live: [elitrobban.se/elbilsladdning](https://elitrobban.se/elbilsladdning/)

---

## Funktioner

- **GPS-baserad sökning** — hittar laddstationer inom 25 km automatiskt
- **38 bilmodeller** — filtrerar stationer baserat på din bils kontakttyp och laddeffekt
- **Snabbast / Billigast** — sortera på DC-effekt eller pris per kWh
- **Kostnadskalkyl** — visar ungefärlig kostnad för 0→100 % baserat på batterikapacitet
- **AI-rekommendation** — Groq LLM ger ett kort råd baserat på din bil och tillgängliga stationer
- **Hemmaladdningstips** — påminner om att hemmaladdning (1,50–3,50 kr/kWh) alltid är billigast

---

## Teknikstack

| Del | Teknologi |
|-----|-----------|
| Backend | Spring Boot 3.2.5 / Java 21 |
| Hosting backend | Render (free tier, Docker) |
| Stationsdata | [Open Charge Map API](https://openchargemap.io) |
| AI | Groq (llama-3.3-70b-versatile) |
| Frontend | Vanilla JS + CSS, inbäddat i WordPress |

---

## Prissättning — en känd begränsning

Laddpriser i Sverige är **svåra att hämta automatiskt** eftersom det saknas ett öppet, standardiserat pris-API för laddoperatörer.

Vad vi har provat och hur vi löst det:

| Källa | Status |
|-------|--------|
| **Chargeprice.app** | Kräver kreditkort även för gratis-tier — ej tillgängligt |
| **API Ninjas EV Charger** | API-nyckeln returnerade "Invalid API Key" — ej tillgängligt |
| **OCM UsageCost-fält** | Finns ibland men är sällan uppdaterat |
| **Statisk operatörstabell** | ✅ Används som fallback — se `OperatorPriceService.java` |

Den statiska tabellen täcker de vanligaste operatörerna i Sverige (Recharge, IONITY, InCharge, Circle K, E.ON, Mer, Allego m.fl.) med priser insamlade manuellt. Priser som kommer från den statiska tabellen markeras med `~` för att visa att de är ungefärliga.

**Priserna kan vara inaktuella** — verifiera alltid hos respektive operatör innan du laddar.

---

## Projektstruktur

```
backend/                         Spring Boot-backend (Render)
  src/main/java/se/elitrobban/elbilsladdning/
    controller/ChargingController.java   REST-endpoints /api/cars och /api/stations
    data/CarDatabase.java                38 bilmodeller med AC/DC-effekt och batterikapacitet
    model/                               CarSpec, StationDto, StationResponse
    service/
      OcmService.java                    Hämtar stationer från Open Charge Map
      OperatorPriceService.java          Statisk prislista för svenska operatörer
      GroqService.java                   AI-rekommendation via Groq
      ChargepriceService.java            Chargeprice-integration (inaktiv)
      ApiNinjasService.java              API Ninjas-integration (inaktiv)

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
| `APININJAS_API_KEY` | API Ninjas (inaktiv — nyckel fungerar ej) |
