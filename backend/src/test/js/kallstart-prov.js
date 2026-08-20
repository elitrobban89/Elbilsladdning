/*
 * Prov för kallstartsvakten och ASSETS-härledningen i ev-app.js.
 *
 * Kör:  node backend/src/test/js/kallstart-prov.js
 *
 * Klipper ut de riktiga blocken ur ev-app.js och kör dem — ingen kopia av logiken finns
 * här. Provet skrevs 2026-08-20 efter att sidan elitrobban.se/elbilsladdning/ visade bara
 * rubriken vid kallstart: datatjänsten ligger på Renders gratisnivå och somnar efter ~15
 * min, och en besökare som landar då ser en sida som laddat men inte fyllts. En tom sida
 * läser som ett fel; en splash läser som att något händer.
 *
 * Två saker provas, och båda är lätta att få subtilt fel:
 *   1. ASSETS måste härledas ur var filen SJÄLV laddades ifrån. Filen finns i två repon
 *      och måste vara identisk i båda, så den får inte peka på en hårdkodad värd — och
 *      framför allt får splashen inte hämtas från den tjänst som sover.
 *   2. Vakten ska larma när svaret DRÖJER, inte när det uteblir. Ett avvisat löfte och ett
 *      långsamt svar ska behandlas likadant: båda betyder "vi väntar".
 */
const fs = require("fs");
const path = require("path");

const APP = path.join(__dirname, "..", "..", "main", "resources", "static", "ev-app.js");
const kalla = fs.readFileSync(APP, "utf8");

/** Klipper ut ett block som börjar på `start` och slutar på raden `slutRad` (exakt match). */
function klipp(start, slutRad, namn) {
  const i = kalla.indexOf(start);
  if (i < 0) { console.error("Hittade inte " + namn + " i ev-app.js — har den döpts om?"); process.exit(1); }
  const rader = kalla.slice(i).split("\n");
  for (let n = 1; n < rader.length; n++) if (rader[n] === slutRad) return rader.slice(0, n + 1).join("\n");
  console.error("Hittade inte slutet på " + namn); process.exit(1);
}

const ASSETS_BLOCK = klipp("  const ASSETS = (function () {", "  })();", "ASSETS");
const VAKT_BLOCK = klipp("  (function kallstartsvakt() {", "  })();", "kallstartsvakten");

let fel = 0;
const ok = (namn, villkor) => {
  console.log((villkor ? "  ok    " : "  FEL   ") + namn);
  if (!villkor) fel++;
};
const vanta = (ms) => new Promise((r) => setTimeout(r, ms));

// ── 1. ASSETS ────────────────────────────────────────────────────────────────
function assetsMed(currentScript) {
  global.document = { currentScript: currentScript };
  let ASSETS;
  eval(ASSETS_BLOCK.replace("  const ASSETS =", "ASSETS ="));
  return ASSETS;
}

console.log("ASSETS härleds ur var filen laddades ifrån");
ok("CarAdvice som värd ger CarAdvice-basen",
   assetsMed({ src: "https://caradvice.onrender.com/ev-app.js" }) === "https://caradvice.onrender.com");
ok("Elbilsladdning som värd ger Elbilsladdning-basen — samma fil, båda repona",
   assetsMed({ src: "https://elbilsladdning.onrender.com/ev-app.js" }) === "https://elbilsladdning.onrender.com");
ok("underkatalog behålls",
   assetsMed({ src: "https://exempel.se/static/ev-app.js" }) === "https://exempel.se/static");
ok("utan currentScript faller den tillbaka på den vakna värden",
   assetsMed(null) === "https://caradvice.onrender.com");

// ── 2. Kallstartsvakten ──────────────────────────────────────────────────────
async function kor(svarEfterMs, avvisa) {
  let replayAnrop = 0;
  const timers = [];
  global.window = {
    evReplaySplash: () => replayAnrop++,
    get EV_COLD_START() { return this._c; },
    set EV_COLD_START(v) { this._c = v; },
  };
  global.document = { querySelector: () => null };
  global.fetch = () => new Promise((res, rej) =>
    setTimeout(() => (avvisa ? rej(new Error("offline")) : res({ ok: true })), svarEfterMs));
  const API = "https://elbilsladdning.onrender.com";
  eval(VAKT_BLOCK);
  await vanta(1400);
  return { kall: !!global.window.EV_COLD_START, replayAnrop };
}

(async () => {
  console.log("\nKallstartsvakten");
  const snabb = await kor(50, false);
  ok("snabbt svar = ingen splash", snabb.kall === false && snabb.replayAnrop === 0);

  const lang = await kor(5000, false);
  ok("svar som dröjer = splash visas", lang.kall === true && lang.replayAnrop === 1);

  const trasig = await kor(5000, true);
  ok("nätverksfel behandlas som väntan, inte som klart", trasig.kall === true);

  // Splashen ligger redan uppe → ta inte över den
  let replay = 0;
  global.window = { evReplaySplash: () => replay++ };
  global.document = { querySelector: () => ({}) };
  global.fetch = () => new Promise((res) => setTimeout(() => res({ ok: true }), 5000));
  const API = "https://elbilsladdning.onrender.com";
  eval(VAKT_BLOCK);
  await vanta(1400);
  ok("ett lager som redan ligger uppe tas inte över", replay === 0 && global.window.EV_COLD_START === true);

  console.log(fel === 0 ? "\nAlla prov gröna" : "\n" + fel + " prov FÖLL");
  process.exit(fel === 0 ? 0 : 1);
})();
