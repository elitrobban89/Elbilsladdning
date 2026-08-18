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

console.log(fel === 0 ? "\nAlla prov gröna\n" : "\n" + fel + " prov föll\n");
process.exit(fel === 0 ? 0 : 1);
