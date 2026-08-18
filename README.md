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
- **Fyndlistan: elbilar som tappat mest i värde** (2026-08-18) — `EvValueRetentionSyncService` räknar varje måndag 04:15 ut hur mycket tio vanliga elbilar har kvar av sitt nypris. **Två källor i samma tal:** nypriset för årsmodell 2021 är seedat i kod från [Kvdbils och Bilprisers artikel](https://www.kvd.se/artiklar/elbilar/sa-haller-10-vanliga-elbilar-sitt-varde) (2024-01-22) — ett nypris för en gammal årsmodell är ett historiskt faktum som inte åldras — medan medianpriset räknas på Blocket vid varje körning. **Därför blir siffran färskare än källan:** artikelns egen "pris idag"-kolumn är från januari 2024, och mätt 08-18 hade ID.4 gått från dess 82 % kvar till 55 %. **`fuel`-filtret är inte valfritt:** sökningen "Volvo XC40 Recharge" gav 43 annonser för 2021 och de flesta var **laddhybrider** (`Recharge T4/T5`) — Volvo kallar både PHEV och elbil för Recharge, så utan filtret hade en elbilslista räknat medianen på laddhybridpriser. Med filtret återstod fyra rena elbilar, under `MIN_ANNONSER`, och modellen hoppas då över i stället. **Trimordet matchas mot `model_specification`**, inte rubriken: `heading` är bara "Nissan Leaf" medan versionen står i specen ("62 kWh N-Connecta"), och ett filter mot rubriken gav noll träffar för fem av sex modeller. Publikt `GET /api/value-retention` (källorna följer med i svaret), admin `POST /api/admin/sync-value-retention`. Visas som **fyndtabell först i tabellkarusellen** och som **faktarad i "Visste du att"** — båda med Kvdbil och Blocket utskrivna
- **Prislogiken är utbruten och provad** (2026-08-18) — `tolkaLaddpris`, `fullLaddningKr`, `verkligaMil`, `krPerMilAv` och `merKostnadMotHemma` ligger mellan markörerna `PRISLOGIK BÖRJAR/SLUTAR` i `ev-app.js`. Låg förut inline på **tre** ställen (stationskorten, chattens stationskontext, laddkalkylen) med var sin kopia av växelkursen; nu finns `EUR_SEK` och `HEMMA_KR_PER_KWH` på var sin rad. **19 prov** körs med `node backend/src/test/js/pris-prov.js`, och **6 prov** för "Visste du att"-kortet med `node backend/src/test/js/funfact-prov.js` (bl.a. att AI-faktat kommer med i sökresultatet — grenen som brast — och att fyndraden uteblir när värdetappslistan är tom) — provfilen klipper ut blocket ur den riktiga ev-app.js och kör det, så ingen kopia av logiken finns i provet. Bakgrund: frontenden är 2 300 rader utan prov, och ett `data is not defined` gick till drift samma dag eftersom en utbrytning bara syntaxkollades — en ReferenceError syns först när raden körs
- **IP-begränsning på stationssök** — max 10 förfrågningar per timme och IP (sliding window), 429 med svensk feltext vid överskridning; IP-poster rensas i schemalagd task varje timme
- **Parallell exekvering** — OCM och NOBIL körs samtidigt; prisberikning för alla 5 stationer körs parallellt via Java 21 virtuella trådar (`newVirtualThreadPerTaskExecutor`); sparar ~1–2 s per sökning vid cold cache
- **Stationslistan är hopfälld** bakom en glödande knapp ("⚡ Visa N kompatibla stationer inom 15 km") som ligger **först i `#ev-output`, alltså direkt under kartan** — `#ev-map` sitter omedelbart ovanför i `elbilsladdning-web.html`, och kartan visar samma stationer som listan. Glödet pulsar bara i hopfällt läge — en knapp som fortsätter blinka när den gjort sitt är bara störande — och stängs av vid `prefers-reduced-motion`. **Öppet läge lever i `state.stationsOpen`, inte i DOM:en:** operatörschippen och sorteringen renderar om hela utdatan, så utan det hade listan fällts ihop mitt under att man filtrerade i den. Kartan ligger i WP-skalet utanför `#ev-output` och påverkas inte av hopfällningen
- **Tipsen visas innan man valt bil** — `renderTipsOnly()` ritar karusellområdet med enbart de statiska fakta direkt vid sidladdning, och en gång till när försäljningstoppen hunnit hem så den dynamiska raden kommer med. Den avstår om `state.lastData` finns (en färdig sökning är rikare) eller om spinnern syns. `buildFunfactHtml(funFact)` och `carouselArea(body)` är egna funktioner just för att båda vägarna in ska ge samma kort och samma rubrik
- **"Visste du att"-karusell** — roterande kortlek med AI-genererat bilfakta, en automatiskt uppdaterad topplista-fakta (se nedan) + 26 statiska fakta (Volvo EX40 mest sålda elbilen 2025 med 8 788 nyregistreringar och ledare även H1 2026, IONITY 350 kW i Norden, Vattenfall InCharge 33 000+ punkter, vinterpåverkan 20–40 %, Tesla Supercharger öppet sedan 2023, Mobility Swedens prognos 45 % elbilsandel 2026, Mercedes CLA Årets Bil 2026, Volvo 16,5 % marknadsandel juni 2026, VW ID.4 månadsetta bland renodlade elbilar maj 2026 med 687 registreringar tätt före Model Y och Polestar 2, EV6/ID.3/Enyaq som rundar av majitoppen, elbilar gick om laddhybrider kring årsskiftet 2025/2026 och passerade diesel i april 2026, de tre senaste källa Carla.se elbilsindex/statistik, uppdateras manuellt månadsvis då sajten blockerar automatisk skrapning; plus **Euro 7 + batteripasset** — batterihälsokravet 80 % efter 5 år/10 000 mil och 72 % efter 8 år/16 000 mil från 29 november 2026, och det digitala batteripasset från 18 februari 2027. **De två ligger medvetet i EN rad**: karusellen visar en slide i taget, och delade upp hade läsaren sett golvet utan passet eller tvärtom. **Och de är två skilda förordningar** — hälsokravet är Euro 7 (EU 2024/1257), passet är EU:s batteriförordning (2023/1542); skriv aldrig att passet är en del av Euro 7) + **6 tips hämtade ur CarAdvice insiktsdatabas** (se nedan); auto-roterar var 9:e sekund med mjuk crossfade-animation (slide glider upp + tonar in); klickbara punkter för manuell navigering. **Paus/spela-knapp** och **förloppslinje** (2026-08-18) — linjen fylls på 9 s, alltså samma takt som rotationen, och fryser mitt i loppet vid paus; utan den ser en pausad karusell likadan ut som en trasig. **Låst höjd:** behållaren mäts mot den högsta sliden vid uppstart och vid resize, annars hoppade kortet i storlek mellan en enradig text och Audi e-tron-stycket — och en slide som lämnar är `position:absolute`, så behållaren hann kollapsa mitt i övergången. **Punktraden bryter rad** (`flex-wrap`) — punkterna har `flex-shrink:0` och kan alltså inte krympa, så 20 fakta ger 286 px punkter mot 254 px innermått vid 320 px viewport och spillde över kortkanten innan wrappen fanns. 14 fakta rymdes, så felet syntes först när tipsen tillkom: **lägg till fler fakta → kontrollera 320 px igen**
- **Tips ur CarAdvice-insikterna** — CarAdvice skrapar nio svenska motorpresskällor till en granskad insiktsdatabas (samma rader som driver bilkortens "Vad experterna säger" där); 153 av de 500 senaste rör laddning, batteri eller förbrukning. Fjorton av dem är inlagda som statiska fakta här (sex i första omgången, åtta i den andra 2026-08-14): förbrukning slår batteristorlek på långresa (Mercedes CLA 250+, 16,5 kWh/100 km och 80 mil på ett 14-minuters stopp), vad laddtiden faktiskt kostar (Volvo EX30, 80 mil på 59 minuters laddning), V2L + V2H som standard (VW ID. Cross), 800 V nedåt i prisklasserna (BYD Atto 3 Evo, 88 → 220 kW), B-läget som praktiskt knep (1–2 mils extra räckvidd) och WLTP-toppen med brasklapp (Mercedes EQS 450+, 925 km). **Urvalsregel: bara laddningsrelevanta rader om bilar som går att köpa idag** — kommande modeller hör hemma i CarAdvice kommande-kö (`insight_upcoming`), inte i en publik faktakarusell, så EX60- och Cupra Raval-raderna valdes bort trots bättre text. Källa anges per tips, som för de befintliga raderna. Hämtas manuellt via CarAdvice `GET /api/admin/insights?limit=500` (X-Admin-Key) — ingen automatisk synk
- **Automatisk elbilstopplista** — `EvSalesRankSyncService` hämtar den 6:e varje månad 05:30 Stockholm (`EvSalesRankSyncScheduler`) elbilsvaruhuset.se:s inlägg "De 10 populäraste elbilarna i Sverige" via öppet WordPress REST-API (`wp-json/wp/v2/posts`, ingen bot-spärr till skillnad från carla.se) — källa Mobility Sweden, renodlat BEV (ingen laddhybrid-/bensinblandning). Tabellen (rank/modell/antal/pris/period) parsas ur riktiga HTML-`<table>`-celler, inte plattad text — modellnamn med siffror ("Polestar 4", "ID.7", "bZ4X") skulle annars blandas ihop med antalskolumnen. Ersätter hela tabellen varje körning (`ev_sales_rank`, ingen historik). Publikt `GET /api/ev-sales-rank`; manuell admin-trigger `POST /api/admin/sync-ev-sales-rank` (X-Admin-Key). Frontend bygger en dynamisk "Visste du att"-fakta av ettan/tvåan varje sidladdning — uppdateras alltså av sig själv utan kodändring.
- **Roterande faktatabeller** — alla fem tabeller cyklar som en karusell under en tydlig "💡 Visste du att?"-avgränsare: lägst uppmätt förbrukning (se nedan), värde för pengarna (km/100 tkr), snabbast DC-laddning, längst räckvidd och WLTP vs verklig räckvidd; föregående/nästa-pilar + punktnavigering + auto-rotate var 9:e sekund; crossfade-transition mellan slides; **Båda karusellerna ligger sedan 2026-08-18 i ett gemensamt område** (`.ev-carousel-area`) under EN rubrik, "💡 AI-tips & Visste du att" — förut låg stationslistan mellan dem, så den som ville jämföra tabellerna fick skrolla förbi fem stationskort varje gång. **Korten sätter `align-items:stretch`:** `.ev-funfact-card` har `align-items:flex-start`, vilket krympte kolumnbarnen så förloppslinjen och knappraden inte fyllde kortet och pilarna hamnade mitt i stället för vid kanterna; **paus/spela-knapp med förloppslinje;** **pausat läge överlever manuell bläddring** — den som pausat för att läsa en tabell kan klicka vidare utan att rotationen smyger igång igen; alla 73 bilar scrollbara med sticky header; vald bil highlightad i blått och auto-scrollad till vid slide-byte; auto-scroll scrollar bara inne i tabellen (inte hela sidan) via `scrollTop` på tabellens scroll-container
- **Tabellen "Lägst uppmätt förbrukning"** — sju bilar ur [Autocars vardagstest](https://www.vibilagare.se/nyheter/volvo-ex30-sticker-ut-en-av-de-torstigaste-elbilarna), refererat av Vi Bilägare 2026-08-17: Ford Puma Gen-E 10,0 · Honda Super-N ~11 · BYD Dolphin Surf ~11 · Volvo ES90 bakhjulsdrift 12,4 · Mini Aceman 14,5 · Citroën ë-C3 15,5 · Volvo EX30 ER Single Motor 17,3 kWh/100 km. Metoden är fyra varv på en bana i 30–80 km/tim med två stopp per varv så att bilen hinner återvinna bromsenergi. Andra kolumnen räknar om till **kWh/mil**, som är måttet svenska förare faktiskt använder. **Egen tabell och inte en kolumn i de andra** av en konkret anledning: de fyra övriga räknar på WLTP ur `ev_spec`, alltså typgodkännande, medan detta är uppmätta värden ur ett enda test med en enda metod — i samma tabell hade de sett jämförbara ut utan att vara det. Poängen med raderna är att **småbil inte är samma sak som snål elbil**: EX30 drar 70 % mer än den ungefär lika stora Puma och 40 % mer än den betydligt större ES90. **Renault 4 och MG4 Urban saknas med flit** — artikeln kallar dem "nästan lika snåla" men ger dem ingen egen siffra, och ett påhittat mätvärde för att fylla en rad är samma fel som fabricerade priser i CarAdvice; de nämns i brödtexten i stället. Samma skäl till att Vi Bilägares egen EX30-långtestsiffra (22,1 kWh/100 km över ett testår med vintertest) står i texten och inte som en rad: annat test, annan skala. Testets egna brasklappar står utskrivna i kortet — förbrukningen är avläst ur bilarnas egna mätare, och alla bilar kördes inte i samma väder. **`kind` sätts explicit på moden** i stället för att läsas ur ikonen (`icon === '🎯'` förr), eftersom radlayouten då satt fast i en emoji och en ikonändring hade gett fel kolumner utan att något gick sönder
- **Livepriser** — Chargeprice API + statisk operatörstabell täcker de flesta svenska nätverk
- **NOBIL-integration** — hämtar antal laddpunkter per station (aktiveras med API-nyckel)
- **Interaktiv karta** — Leaflet + OpenStreetMap (gratis, ingen API-nyckel, serveras lokalt utan CDN-beroende) visas ovanför stationslistan; färgkodade markörer: 🟢 grön ≥100 kW, 🟠 orange ≥22 kW, 🟣 lila långsam; din position som blå cirkel; klicka markör för popup med kW, kontakttyp, pris och avstånd; zoomnivå 17
- **Ruttplanering** — kollapsbar panel "Planera rutt" under kontrollerna; GPS-position används automatiskt som start; ange bara destination (t.ex. "Göteborg"); backend beräknar antal laddstoppar (75 % av WLTP per etapp) och söker närmaste kompatibla station per hållplats via OCM; kartan visar hela rutten som en verklig väglinje via [OSRM](https://project-osrm.org/) med faktisk motorvägsgeometri (E4, E6 osv.), gul A-markör vid start, numrerade gula markörer vid laddstoppar, röd B-markör vid mål; rubrikraden visar vägavstånd och uppskattad körtid; faller tillbaka på streckad rak linje om OSRM är otillgänglig; OSRM och laddstationssökning körs parallellt — ingen extra väntetid
- **Stationer kollapsar vid ruttplanering** — när ruttresultat visas minimeras den lokala stationslistan automatiskt till en klickbar rad ("📍 X stationer nära dig · visa ▾"); kartan och ruttresultatet tar fokus; klick på raden återställer stationslistan
- **Proaktiv ruttchatt** — chatboten öppnas automatiskt och sammanfattar rutten och laddstoppet direkt när ruttplanering slutförts, utan att användaren behöver fråga; svaret streamar token för token och sparas i chatthistoriken. **Klarar bilen hela sträckan utan laddstopp bekräftar boten det uttryckligen** (t.ex. "Din ES90 klarar hela sträckan till Stockholm utan laddstopp") innan den ger reseråd — promptregeln är obligatorisk, inte valfri
- **AI-chattbot** ⚡ — flytande chattassistent (knapp nere till höger med animerade blixtar) driven av Groq; polerad glassmorphism-design med öppningsanimation, grön pulserande online-indikator och underrubrik "AI • Laddning & Elbilar"; stödjer markdown i bot-svar (fetstil, listor); rensa-knapp i headern; svarar ENDAST på frågor om elbilar, laddning, räckvidd och stationer; smarta budgetregler (budget under 200 tkr → föreslår begagnade med prisintervall, rekommenderar aldrig ny bil >1,3x budgeten); stödjer flerturskonversation; max 10 frågor/minut per IP
- **Demoläge på chatboten** — utloggade får **3 gratis frågor** (chatboten är det enda som syns utan konto); en demobar visar "N gratis frågor kvar" och 4:e frågan visar en inloggnings-CTA. `ev_demo_count` i `localStorage`, inloggning verifieras mot CarAdvice `/api/auth/me` (`ca_token`, samma konto som övriga tjänster); lyssnar på inloggningspopupen (CA_LOGIN/CA_SUBSCRIBED/CA_LOGOUT). Omförsök och info-knappen räknas aldrig mot gränsen
- **Streaming-svar** — chattbotens svar strömmar direkt token för token via `/api/chat/stream` (SSE) utan att vänta på hela svaret; automatisk fallback till vanlig JSON-endpoint om webbläsaren saknar ReadableStream-stöd
- **Dynamiska follow-up chips** — efter varje svar visas 2–3 kontextuella snabbknappar baserade på svarsinnehållet (räckvidd, laddning, pris, bilmodeller)
- **Full appkontext till chattboten** — chatboten får automatiskt hela skärmens kontext: vald bil (batteri, DC, räckvidd, pris), laddtidskalkylatorn (från/till%, tid, kostnad, tillkommen räckvidd), planerad rutt (destination, alla laddstoppar med avstånd och pris), DC-ranking topp 3, räckvidsranking topp 3, AI-rekommendation och faktaruta visad på skärmen; möjliggör naturliga följdfrågor på allt som visas
- **Prenumerationsinfo** — chatboten svarar korrekt på frågor om prenumerationen: 49 kr/mån inkluderar AI Bilrådgivning, AI EV Laddassistent och Bränslekostnadsberäkning
- **"Info & prenumeration"-knapp** — alltid synlig i chatten (även demobaren för utloggade); visar ett statiskt info-kort (ingen AI/backend-anrop, alltid gratis) med alla tre tjänster **obegränsade** för prenumeranter mot begränsade demoversioner + en Prenumerera-knapp som öppnar `subscribe.html?from=elbilsladdning`
- **Billigaste laddning** — vid fråga om billigaste laddning rekommenderas alltid hemmaladdning (~1,50–3,50 kr/kWh) först, följt av billigaste publik station i listan med namn, pris och avstånd
- **Ruttkontext** — om en rutt är planerad kan chattboten förklara varför ett specifikt laddstop valts, om bilen klarar sträckan utan stopp, och hur stopparna är utplacerade längs vägen
- **Laddtidskalkylator** — interaktiv kalkylator visas under sökresultaten: dra sliders "Ladda från X% till Y%" och se beräknad tid, kostnad och tillkommen räckvidd i realtid; använder vald bils batteristorlek och den närmaste DC-stationens effektiva kW
- **Expandera-läge i chatten** — chevron-knapp i chattens header växlar mellan normalt bottenkort och stort läge: helskärmsark på mobil (FAB:en döljs), 560 px bred panel med full höjd på desktop. Läget sparas i `localStorage` (`ev-chat-max`) och överlever omladdning. Att FAB:en döljs bara när panelen både är expanderad och öppen är en ren CSS-regel — se nästa punkt
- **Klassbaserat chattillstånd** — chattens tillstånd bor i klasser, aldrig i inline `style.display`. Öppet/stängt är `body.ev-chat-open`, expanderat är `body.ev-chat-max`, och snabbknappsraden döljs med `.ev-chat-quick-off` när samtalet börjat. Poängen är att CSS då kan uttrycka villkor som JS annars måste hålla synkade: `body.ev-chat-open.ev-chat-max .ev-chat-fab-wrap { display: none }` döljer flytknappen exakt när panelen är både expanderad och öppen, och `body.ev-chat-max .ev-chat-quick:not(.ev-chat-quick-off)` tar tillbaka snabbknapparna i liggande expanderat läge utan att krocka med JS-tillståndet. Inline `display` vinner över hela cascaden, så varje CSS-regel på samma element hade blivit tyst verkningslös — det var den buggen som gjorde att snabbknapparna dök upp igen i liggande läge efter en rensning. Enda vägen in i öppet-läget är `chatIsOpen()` / `chatSetOpen()`
- **Mobilanpassad chattpanel** — chatten är ett bottenkort som lämnar sidan bakom synlig: höjdtaket är `min(440px, 58dvh)` (`dvh` så att mobilens adressfält inte spräcker höjden) och panelen spänner `left/right: 10px` i stället för fast bredd. Brytpunkten ligger på 640 px så att även bredare telefoner (Pixel 412 px, iPhone Pro Max 430 px) träffas. I liggande läge (`max-height: 480px`) sänks taket till `min(300px, 70dvh)` och snabbknapparna döljs
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

83 backendtester i tre lager — ren logik, HTTP-felvägar och controller-lagret (MockMvc, tjänsterna mockas):

| Testklass | Täcker |
|-----------|--------|
| `RouteServiceTest` (9) | Ruttmatte: haversine, stopp utifrån 75 % av räckvidden, stationsval per stopp |
| `CarSpecServiceTest` (8) | Entitetsmappning, dubblettdedup, kontakttyper (CCS/CHAdeMO), DB-fel → hårdkodad fallback, cache |
| `OperatorPriceServiceTest` (12) | Operatörsmatchning, stationsnamnsfallback, OCM-placeholder ignoreras |
| `GroqServiceTest` (9) | Promptbygget (topplistor, stationslista, kostnadsjämförelse), fallbacksvar, 429-retry-parsning, sektionsextraktion |
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
