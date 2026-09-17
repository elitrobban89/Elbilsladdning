/*
 * Prov för Vroom-kortet i ev-app.js.
 *
 * Kör:  node backend/src/test/js/vroom-prov.js
 *
 * Klipper ut buildVroomHtml ur den riktiga ev-app.js och KÖR den — ingen kopia av logiken
 * finns här. Samma skäl som funfact-provet: en ReferenceError syns först när raden körs, och
 * just den här funktionen körs bara när backenden svarat med data, alltså aldrig lokalt.
 */
const fs = require("fs");
const path = require("path");

const APP = path.join(__dirname, "..", "..", "main", "resources", "static", "ev-app.js");
const kalla = fs.readFileSync(APP, "utf8");

const START = "  function buildVroomHtml() {";
const i = kalla.indexOf(START);
if (i < 0) {
  console.error("Hittade inte buildVroomHtml i ev-app.js — har den döpts om?");
  process.exit(1);
}
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

// esc() bor någon annanstans i filen och är kortets enda yttre beroende. Den skickas in i
// stället för att klippas ut också — provet gäller kortet, inte escapningen.
function esc(s) {
  return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;")
                  .replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

function bygg(state) {
  return new Function("state", "esc", block + "\n  return buildVroomHtml();")(state, esc);
}

const TOPP = {
  manad: "augusti 2026", kalla: "Vroom", sammanstalltAv: "Studio Esse",
  lank: "https://www.youtube.com/watch?v=L0PRKpNZMZE",
  bilar: [
    { plats: 1, modell: "EX40", marke: "Volvo", antal: 731 },
    { plats: 2, modell: "ID.7", marke: "Volkswagen", antal: 515 },
    { plats: 3, modell: "EV3", marke: "Kia", antal: 485 }
  ]
};

const NYHETER = {
  manad: "september 2026", kalla: "Vroom", kallLank: "https://www.mynewsdesk.com/se/vroom",
  nyheter: [
    { rubrik: "Begagnatmarknaden möter nya utmaningar", sammanfattning: "Minskade med 12 %.",
      lank: "https://www.mynewsdesk.com/se/vroom/pressreleases/x", datum: "2026-09-08", manad: "september 2026" }
  ]
};

const tomt = { vroomTopp: null, vroomNyheter: null };

console.log("\nVroom-kortet ur ev-app.js\n");

prov("utan data byggs INGET kort — fliken ska inte finnas alls", () => {
  const h = bygg(tomt);
  if (h !== "") throw new Error("förväntade tom sträng, fick " + h.length + " tecken");
});

prov("topplistan renderas med plats, modell, märke och antal", () => {
  const h = bygg({ ...tomt, vroomTopp: TOPP });
  if (!h.includes("ev-vroom-carousel")) throw new Error("karusellkortet saknas");
  if (!h.includes("EX40")) throw new Error("modellen saknas");
  if (!h.includes("Volvo")) throw new Error("märket saknas");
  if (!h.includes("731")) throw new Error("antalet saknas");
  if (!h.includes("augusti 2026")) throw new Error("månaden saknas i rubriken");
});

prov("källan står utskriven med Vroom först", () => {
  const h = bygg({ ...tomt, vroomTopp: TOPP });
  if (!h.includes("Källa: <strong>Vroom</strong>")) throw new Error("Vroom saknas som källa");
  if (!h.includes("Studio Esse")) throw new Error("sammanställaren saknas");
  if (!h.includes('rel="noopener"')) throw new Error("extern länk utan noopener");
});

prov("rubriken säger elbilar, inte personbilar", () => {
  // Källan skriver "Topp 25 personbilar" men listan är renodlat BEV — se VroomTopCarsService.
  const h = bygg({ ...tomt, vroomTopp: TOPP });
  if (!h.includes("Mest registrerade elbilarna")) throw new Error("fel rubrik");
});

prov("nyheterna blir en slide var, efter topplistan", () => {
  const h = bygg({ ...tomt, vroomTopp: TOPP, vroomNyheter: NYHETER });
  const antal = h.split("ev-vroom-slide").length - 1;
  if (antal !== 2) throw new Error("förväntade 2 slides, fick " + antal);
  if (h.indexOf("EX40") > h.indexOf("Begagnatmarknaden")) throw new Error("topplistan ska ligga först");
});

prov("nyheterna ensamma: första sliden syns ändå", () => {
  // Utan den här raden ärvde slide 1 sitt display:none från nyhetsmallen och kortet stod tomt.
  const h = bygg({ ...tomt, vroomNyheter: NYHETER });
  const forstaSlide = h.slice(h.indexOf("ev-vroom-slide"));
  if (forstaSlide.slice(0, 120).includes("display:none")) throw new Error("första sliden är dold");
  if (!forstaSlide.includes("display:flex")) throw new Error("första sliden saknar display:flex");
});

prov("prickarna följer antalet slides", () => {
  const h = bygg({ ...tomt, vroomTopp: TOPP, vroomNyheter: NYHETER });
  const prickar = h.split("ev-fact-dot").length - 1;
  // 2 slides = 2 prickar, plus klassen ev-fact-dot-active som innehåller samma delsträng
  if (prickar !== 3) throw new Error("förväntade 2 prickar (3 träffar), fick " + prickar);
});

prov("text utifrån escapas — kortet skrivs med innerHTML", () => {
  const h = bygg({ ...tomt, vroomNyheter: { ...NYHETER, nyheter: [{
    rubrik: "<img src=x onerror=alert(1)>", sammanfattning: "a & b", lank: "", datum: "2026-09-08" }] } });
  if (h.includes("<img src=x")) throw new Error("rubriken escapades inte");
  if (!h.includes("&lt;img")) throw new Error("förväntade escapad rubrik");
  if (!h.includes("a &amp; b")) throw new Error("sammanfattningen escapades inte");
});

prov("nyhet utan länk får ingen tom länkrad", () => {
  const h = bygg({ ...tomt, vroomNyheter: { ...NYHETER, nyheter: [{
    rubrik: "R", sammanfattning: "S", lank: "", datum: "2026-09-08" }] } });
  if (h.includes("Läs hela pressmeddelandet")) throw new Error("länkraden ritades utan länk");
});

console.log(fel ? "\n" + fel + " prov FÖLL\n" : "\nAlla prov gröna\n");
process.exit(fel ? 1 : 0);
