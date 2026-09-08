/*
 * Prov för "Visste du att"-kortet i ev-app.js.
 *
 * Kör:  node backend/src/test/js/funfact-prov.js
 *
 * Klipper ut buildFunfactHtml ur den riktiga ev-app.js och kör den — ingen kopia av logiken
 * finns här. Provet skrevs 2026-08-18 efter att en utbrytning av just den funktionen skickade
 * "data is not defined" till drift: en ReferenceError syns först när raden KÖRS, och den
 * raden kördes bara när AI:n gett ett svar. En syntaxkoll hade aldrig fångat det.
 */
const fs = require("fs");
const path = require("path");

const APP = path.join(__dirname, "..", "..", "main", "resources", "static", "ev-app.js");
const kalla = fs.readFileSync(APP, "utf8");

const START = "  function buildFunfactHtml(funFact) {";
const i = kalla.indexOf(START);
if (i < 0) {
  console.error("Hittade inte buildFunfactHtml i ev-app.js — har den döpts om?");
  process.exit(1);
}
// Funktionen slutar på första raden som är exakt "  }" efter starten.
const rader = kalla.slice(i).split("\n");
let slut = -1;
for (let n = 1; n < rader.length; n++) if (rader[n] === "  }") { slut = n; break; }
if (slut < 0) { console.error("Hittade inte funktionens slut"); process.exit(1); }
const block = rader.slice(0, slut + 1).join("\n");

let fel = 0;
function prov(namn, fn) {
  try { fn(); console.log("  ok    " + namn); }
  catch (e) { fel++; console.log("  FEL   " + namn + "\n          " + e.message); }
}

/** Kör funktionen med ett givet state. */
function bygg(state, funFact) {
  return new Function("state", block + "\n  return buildFunfactHtml(" + JSON.stringify(funFact || null) + ");")(state);
}

const tomtState = { evSalesRank: [], valueRetention: [], valueRetentionKalla: "" };

console.log("\nFaktakarusellen ur ev-app.js\n");

prov("förstavyn: utan bil, utan AI-fakta, utan synkad data", () => {
  const h = bygg(tomtState, null);
  if (!h.includes("ev-funfact-carousel")) throw new Error("karusellen saknas");
  if (!h.includes("Euro 7")) throw new Error("de statiska fakta saknas");
});

prov("AI-faktat kommer med i sökresultatet — grenen som brast 2026-08-18", () => {
  const h = bygg(tomtState, "AI-genererat testfakta 12345");
  if (!h.includes("AI-genererat testfakta 12345")) throw new Error("funFact kom inte med");
});

prov("topplisteraden byggs av försäljningsstatistiken", () => {
  const h = bygg({ ...tomtState, evSalesRank: [
    { model: "Volvo EX40", units: 8788, periodLabel: "2025" },
    { model: "Tesla Model Y", units: 5000 }] }, null);
  if (!h.includes("Volvo EX40")) throw new Error("topplisteraden saknas");
});

prov("fyndraden byggs av värdetappslistan, med BÅDA källorna utskrivna", () => {
  const h = bygg({ ...tomtState,
    valueRetention: [{ model: "Audi e-tron 55 quattro", retentionPct: 38, newPriceKr: 970000,
                       medianPriceKr: 368600, cheapestPriceKr: 299000, adCount: 28 }],
    valueRetentionKalla: "Nypris enligt Kvdbil och Bilpriser (kvd.se, 2024-01-22)" }, null);
  if (!h.includes("Audi e-tron 55 quattro")) throw new Error("modellen saknas");
  if (!h.includes("62 %")) throw new Error("värdetappet ska visas som 100 - kvar, alltså 62 %");
  if (!h.includes("Kvdbil")) throw new Error("nyprisets källa måste stå utskriven");
  if (!h.includes("Blocket")) throw new Error("medianprisets källa måste stå utskriven");
  if (!h.includes("28 annonser")) throw new Error("underlaget måste framgå");
});

prov("fyndraden UTEBLIR när värdetappslistan är tom", () => {
  const h = bygg(tomtState, null);
  if (h.includes("Fyndläge på begagnad el:")) throw new Error("fyndraden byggdes utan data");
});

prov("saknad billigast-uppgift fäller inte raden", () => {
  const h = bygg({ ...tomtState,
    valueRetention: [{ model: "Testbil", retentionPct: 50, newPriceKr: 400000,
                       medianPriceKr: 200000, cheapestPriceKr: null, adCount: 7 }],
    valueRetentionKalla: "källa" }, null);
  if (!h.includes("Testbil")) throw new Error("raden byggdes inte");
  if (h.includes("billigaste exemplaret på null")) throw new Error("null läckte ut i texten");
});

// ── De tre dynamiska raderna som kom till 2026-09-08 ────────────────────────
// Proven räknar EFTER, de kollar inte bara att en rad finns: hela poängen med att räkna
// siffrorna i stället för att skriva dem för hand är att de blir rätt, och en rad som visar
// fel tal är sämre än ingen rad alls.

prov("laddprisraden räknar skillnaden och kvoten ur operatörstabellen", () => {
  const h = bygg({ ...tomtState, laddpriser: {
    cheapest: { operator: "Lidl", priceKr: 2.99 },
    priciest: { operator: "Ionity", priceKr: 6.96 },
    avgNationalKr: 4.71 } }, null);
  if (!h.includes("Lidl") || !h.includes("Ionity")) throw new Error("nätverken saknas");
  if (!h.includes("2,99 kr/kWh")) throw new Error("priset ska skrivas med decimalkomma");
  // (6,96 - 2,99) × 50 kWh = 198,50 → 199 kr
  if (!h.includes("199 kr")) throw new Error("skillnaden per 50 kWh räknades fel");
  // 6,96 / 2,99 = 2,33 → 2,3 gånger
  if (!h.includes("2,3 gånger")) throw new Error("kvoten räknades fel");
  if (!h.includes("4,71 kr/kWh")) throw new Error("snittet saknas");
});

prov("laddprisraden UTEBLIR utan tabelluppgifter", () => {
  const h = bygg(tomtState, null);
  if (h.includes("Var du laddar avgör priset")) throw new Error("raden byggdes utan data");
});

prov("elområdesraden tar lägsta mot högsta zonen och bär brasklappen", () => {
  const h = bygg({ ...tomtState, elzoner: [
    { zone: "SE1", spot: 0.21 }, { zone: "SE3", spot: 0.58 },
    { zone: "SE4", spot: 0.94 }, { zone: "SE2", spot: 0.24 }] }, null);
  if (!h.includes("21 öre/kWh") || !h.includes("SE1")) throw new Error("lägsta zonen fel");
  if (!h.includes("94 öre") || !h.includes("SE4")) throw new Error("högsta zonen fel");
  if (!h.includes("73 öre</strong> i skillnad")) throw new Error("skillnaden räknades fel");
  // Utan brasklappen jämför läsaren spotpris med sin elräkning och tror att vi räknat fel
  if (!h.includes("energiskatt")) throw new Error("brasklappen om skatt/nät/moms saknas");
});

prov("elområdesraden UTEBLIR när zonerna kostar lika mycket", () => {
  const h = bygg({ ...tomtState, elzoner: [
    { zone: "SE1", spot: 0.502 }, { zone: "SE4", spot: 0.504 }] }, null);
  if (h.includes("i skillnad på spotpriset")) throw new Error("raden byggdes utan en skillnad att visa");
});

prov("effektraden räknar bilarna i databasen och pekar ut den snabbaste", () => {
  const bilar = [];
  for (let i = 0; i < 6; i++) bilar.push({ name: "Snabb " + i, maxDcKw: 180 });
  for (let i = 0; i < 4; i++) bilar.push({ name: "Långsam " + i, maxDcKw: 50 });
  bilar.push({ name: "Toppbilen", maxDcKw: 350 });
  // Utan DC alls ska inte räknas med bland de snabbladdande
  bilar.push({ name: "Zoe", maxDcKw: 0 });
  const h = bygg({ ...tomtState, cars: bilar }, null);
  if (!h.includes("<strong>11</strong> snabbladdande")) throw new Error("Zoe utan DC räknades med");
  if (!h.includes("<strong>7</strong> minst 150 kW")) throw new Error("antalet över 150 kW fel");
  if (!h.includes("<strong>4</strong> ligger under 100 kW")) throw new Error("antalet under 100 kW fel");
  if (!h.includes("Toppbilen")) throw new Error("den snabbaste bilen pekas inte ut");
  if (!h.includes("350 kW")) throw new Error("toppeffekten saknas");
});

prov("effektraden UTEBLIR när bildatabasen inte hunnit hem", () => {
  const h = bygg({ ...tomtState, cars: [{ name: "Enda bilen", maxDcKw: 150 }] }, null);
  if (h.includes("Stolpens effekt är sällan taket")) throw new Error("raden byggdes på ett för tunt underlag");
});

console.log(fel === 0 ? "\nAlla prov gröna\n" : "\n" + fel + " prov föll\n");
process.exit(fel === 0 ? 0 : 1);
