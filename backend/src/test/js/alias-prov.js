/*
 * Volvos namnbyten: C40 Recharge -> EC40, XC40 Recharge -> EX40.
 *
 * Kör:  node backend/src/test/js/alias-prov.js
 *
 * BÅDA namnen står kvar i datan med flit. En två-tre år gammal bil — alltså precis den man
 * köper begagnad — heter fortfarande det gamla namnet i annonsen, och specarna skiljer sig:
 * XC40 Recharge ligger på 75 kWh och 150 kW DC, EX40 på 79 kWh och 207 kW. Att döpa om raden
 * hade gett en begagnad bil fel laddeffekt, och laddeffekten är hela poängen med appen.
 * De är samma modellinje i olika åldrar, inte dubbletter.
 *
 * Provet klipper ut den RIKTIGA funktionen ur ev-app.js. Skrivs den av för hand provar man
 * sin egen kopia och inte appen.
 */
const fs = require("fs");
const path = require("path");

const APP = fs.readFileSync(
  path.join(__dirname, "..", "..", "main", "resources", "static", "ev-app.js"), "utf8");
const start = APP.indexOf("  const NAMNBYTEN = [");
const slut = APP.indexOf("  function skapaMarkesvaljare");
if (start < 0 || slut < 0) throw new Error("hittade inte namnbytesblocket i ev-app.js");
const namnbyteFor = new Function(APP.slice(start, slut) + "\n return namnbyteFor;")();

// Namnen är hämtade ur den riktiga ev_spec-tabellen, inte påhittade.
const PROV = [
  ["Volvo EC40 Twin Motor",             "samma bil som C40 Recharge",  "C40"],
  ["Volvo C40 Single Motor",            "heter EC40",                  "EC40"],
  ["Volvo EX40 Single Motor",           "samma bil som XC40 Recharge", "XC40"],
  ["Volvo XC40 Recharge Twin",          "heter EX40",                  "EX40"],
  // Ordgränsen: C40 får ALDRIG matcha inuti EC40, och inget av namnen får smitta grannarna
  ["Volvo EX30 Twin Motor Performance", null, null],
  ["Volvo EX90 Single Motor",           null, null],
  ["Volvo EX60",                        null, null],
  ["Volvo XC60 T8",                     null, null]
];

let fel = 0;
for (const [namn, vantadNotis, vantatOcksa] of PROV) {
  const r = namnbyteFor(namn);
  const ok = vantadNotis === null
    ? r === null
    : r !== null && r.notis.indexOf(vantadNotis) === 0 && r.ocksa === vantatOcksa;
  if (!ok) fel++;
  console.log((ok ? "  ok    " : "  FEL   ") + namn.padEnd(34) + " -> "
    + (r ? r.notis + "   [sök: " + r.ocksa + "]" : "(ingen notis)"));
}

console.log(fel ? "\n" + fel + " prov FÖLL" : "\nAlla prov gröna");
process.exit(fel ? 1 : 0);
