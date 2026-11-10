/* Elbilsladdning — (c) 2026 Robert Andersson Kopler. Alla rattigheter forbehallna. */
/*
 * Prov för videokortet i ev-app.js.
 *
 * Kör:  node backend/src/test/js/video-prov.js
 *
 * Klipper ut buildVideoHtml ur den riktiga ev-app.js och KÖR den, samma upplägg som
 * vroom-prov.js: funktionen körs bara när backenden svarat, alltså aldrig lokalt.
 */
const fs = require("fs");
const path = require("path");

const APP = path.join(__dirname, "..", "..", "main", "resources", "static", "ev-app.js");
const kalla = fs.readFileSync(APP, "utf8");

const START = "  function buildVideoHtml() {";
const i = kalla.indexOf(START);
if (i < 0) {
  console.error("Hittade inte buildVideoHtml i ev-app.js — har den döpts om?");
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

function esc(s) {
  return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;")
                  .replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

function bygg(state) {
  return new Function("state", "esc", block + "\n  return buildVideoHtml();")(state, esc);
}

const LISTA = {
  kanal: "Alriks Bilar", kanalLank: "https://www.youtube.com/@alriksbilar",
  videor: [
    { id: "rcq_ny8kZrA", titel: "Därför är Volvos kris vår kris", kanal: "Alriks Bilar", datum: "2026-10-29", utvald: false },
    { id: "z1AENQtVddg", titel: "Test: BÄSTA FAMILJE-ELBILEN?", kanal: "Alriks Bilar", datum: "2026-08-31", utvald: true }
  ]
};

console.log("\nVideokortet ur ev-app.js\n");

prov("utan data byggs INGET kort — fliken ska inte finnas alls", () => {
  if (bygg({ bilvideor: null }) !== "") throw new Error("förväntade tom sträng");
});

prov("en slide per video, första synlig, resten dolda", () => {
  const h = bygg({ bilvideor: LISTA });
  if (!h.includes("ev-video-carousel")) throw new Error("karusellkortet saknas");
  const delar = h.split('class="ev-video-slide"').slice(1);
  if (delar.length !== 2) throw new Error("förväntade 2 slides, fick " + delar.length);
  if (!delar[0].startsWith(' style="display:flex')) throw new Error("första sliden syns inte");
  if (!delar[1].startsWith(' style="display:none')) throw new Error("andra sliden är inte dold");
});

prov("länk och miniatyr byggs av id:t", () => {
  const h = bygg({ bilvideor: LISTA });
  if (!h.includes("https://www.youtube.com/watch?v=rcq_ny8kZrA")) throw new Error("videolänken saknas");
  if (!h.includes("https://i.ytimg.com/vi/rcq_ny8kZrA/hqdefault.jpg")) throw new Error("miniatyren saknas");
  if (!h.includes('rel="noopener"')) throw new Error("extern länk utan noopener");
});

prov("senaste och utvald märks olika", () => {
  const h = bygg({ bilvideor: LISTA });
  if (!h.includes("Senaste · Alriks Bilar · 2026-10-29")) throw new Error("etiketten för senaste saknas");
  if (!h.includes("Utvald · Alriks Bilar · 2026-08-31")) throw new Error("etiketten för utvald saknas");
});

prov("försäljningsvideon märks Försäljning, inte Senaste", () => {
  const h = bygg({ bilvideor: { ...LISTA, videor: [
    { id: "3dHcq44U1q8", titel: "Elbilsförsäljningen oktober 2026", kanal: "Studio Esse", datum: "2026-11-04", utvald: false, forsaljning: true } ] } });
  if (!h.includes("Försäljning · Studio Esse · 2026-11-04")) throw new Error("etiketten för försäljning saknas");
  if (h.includes("Senaste · Studio Esse")) throw new Error("försäljningsvideon märktes Senaste");
});

prov("ett id som inte ser ut som YouTubes slängs — inget främmande i src", () => {
  const h = bygg({ bilvideor: { ...LISTA, videor: [
    { id: '"><script>x</script>', titel: "Ond", kanal: "X", datum: "2026-10-30", utvald: false },
    LISTA.videor[0] ] } });
  if (h.includes("<script>")) throw new Error("ondsint id kom med");
  if (h.split('class="ev-video-slide"').length - 1 !== 1) throw new Error("förväntade 1 slide");
});

prov("bara ogiltiga id:n ger inget kort", () => {
  if (bygg({ bilvideor: { ...LISTA, videor: [{ id: "kort", titel: "T", kanal: "K", datum: "d" }] } }) !== "")
    throw new Error("kort ritades utan giltig video");
});

prov("titel och kanal escapas — kortet skrivs med innerHTML", () => {
  const h = bygg({ bilvideor: { ...LISTA, videor: [
    { id: "rcq_ny8kZrA", titel: "<img src=x onerror=alert(1)>", kanal: "A & B", datum: "2026-10-29" }] } });
  if (h.includes("<img src=x")) throw new Error("titeln escapades inte");
  if (!h.includes("A &amp; B")) throw new Error("kanalen escapades inte");
});

prov("prickarna följer antalet slides", () => {
  const h = bygg({ bilvideor: LISTA });
  if (h.split('data-dot="').length - 1 !== 2) throw new Error("fel antal prickar");
});

console.log(fel ? "\n" + fel + " prov FALLERADE\n" : "\nAlla prov gick igenom\n");
process.exit(fel ? 1 : 0);
