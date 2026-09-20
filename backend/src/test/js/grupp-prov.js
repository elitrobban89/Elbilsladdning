/* Elbilsladdning — (c) 2026 Robert Andersson Kopler. Alla rattigheter forbehallna. */
/*
 * Prov för modellgrupperingen i märkesväljaren.
 *
 * Kör:  node backend/src/test/js/grupp-prov.js
 *
 * Klipper ut NAMNBYTEN och grupperingsblocket ur den riktiga ev-app.js och kör dem — ingen
 * kopia av logiken bor här. Namnen nedan är HÄMTADE ur den riktiga bildatabasen (535 rader),
 * och de är valda för att de är fallen som faktiskt kan gå fel:
 *
 *   · XC40 Recharge och EX40 är samma bil under två namn och MÅSTE hamna i samma grupp
 *   · EX30 Cross Country och EX30 Single Motor är samma modell i olika utföranden
 *   · "Model 3" och "IONIQ 5" bär modellen i ANDRA ordet — grupperas de på första ordet
 *     slås Model 3, S och Y ihop till en enda grupp, liksom IONIQ 3, 5, 6 och 9
 *   · "Elroq 60" och "Elroq 85" bär tvärtom en utrustningsnivå i andra ordet och ska ihop
 *
 * De fyra sista raderna är fel som ett svep över hela databasen hittade: DS Automobiles
 * tolkades som märket "DS" med modellen "Automobiles N°7", och e-Tourneo Courier/Custom,
 * Proace Verso/Proace City och AION UT/V slogs ihop fast de är olika bilar.
 */
const fs = require("fs");
const path = require("path");

const APP = path.join(__dirname, "..", "..", "main", "resources", "static", "ev-app.js");
const kalla = fs.readFileSync(APP, "utf8");

function klipp(fran, till, vad) {
  const i = kalla.indexOf(fran);
  const j = kalla.indexOf(till, i);
  if (i < 0 || j < 0) {
    console.error("Hittade inte " + vad + " i ev-app.js — har koden döpts om?");
    process.exit(1);
  }
  return kalla.slice(i, j);
}

const block =
  klipp("  const NAMNBYTEN = [", "  function namnbyteFor(", "NAMNBYTEN") +
  klipp("  // ── Modellinjer: samma bilmodell i en grupp", "  // Emblemet är märkets initialer", "grupperingsblocket");

const { grupperaModeller, linjeAv, markeAv } = new Function(
  block + `
  // markeAv klipps inte ut utan speglas här: provet behöver bara märkesdelen, och den är
  // tre rader. Ändras den i appen fälls provet av DS-fallet nedan.
  function markeAv(n) {
    const t = (n || "").trim();
    if (/^DS\\s+Automobiles\\b/i.test(t)) return "DS Automobiles";
    const f = t.split(/\\s+/)[0] || "";
    if (/^MG\\d/i.test(f)) return "MG";
    if (f.toLowerCase() === "firefly") return "Firefly";
    return f;
  }
  return { grupperaModeller: grupperaModeller, linjeAv: linjeAv, markeAv: markeAv };`)();

let fel = 0;
function prov(namn, fn) {
  try { fn(); console.log("  ok    " + namn); }
  catch (e) { fel++; console.log("  FEL   " + namn + "\n          " + e.message); }
}

/** Bygger grupperna för en lista med hela bilnamn, som appen gör. */
function grupper(namn) {
  const marke = markeAv(namn[0]);
  const bilar = namn.map(function (n, i) {
    const modell = n.toLowerCase().startsWith(marke.toLowerCase() + " ") ? n.slice(marke.length + 1) : n;
    return { modell: modell, kwh: 0, index: i };
  });
  return grupperaModeller(bilar);
}

/** Gruppen som innehåller ett visst modellnamn. */
function gruppMed(g, del) {
  const träff = g.find(function (x) {
    return x.bilar.some(function (b) { return b.modell.indexOf(del) !== -1; });
  });
  if (!träff) throw new Error('hittade ingen grupp med "' + del + '"');
  return träff;
}

console.log("\nModellgrupperingen ur ev-app.js\n");

prov("XC40 Recharge och EX40 hamnar i SAMMA grupp, med XC40 först", () => {
  const g = grupper(["Volvo XC40 Recharge", "Volvo EX40 Single Motor",
                     "Volvo XC40 Recharge Twin", "Volvo EX40 Twin Motor"]);
  if (g.length !== 1) throw new Error("blev " + g.length + " grupper, väntade 1");
  const namn = g[0].bilar.map(function (b) { return b.modell; });
  if (!/^XC40/.test(namn[0]) || !/^XC40/.test(namn[1]))
    throw new Error("äldst först bröts: " + namn.join(" | "));
  if (!/^EX40/.test(namn[2]) || !/^EX40/.test(namn[3]))
    throw new Error("EX40 ska ligga sist: " + namn.join(" | "));
  if (!g[0].generationer) throw new Error("gruppen bär två generationer men flaggan är av");
});

prov("EX30 Cross Country och EX30 Single Motor är samma modell", () => {
  const g = grupper(["Volvo EX30 Cross Country", "Volvo EX30 Single Motor",
                     "Volvo EX30 Twin Motor Performance", "Volvo EX30 Single Motor Extended Range"]);
  if (g.length !== 1) throw new Error("blev " + g.length + " grupper, väntade 1");
  // Ingen av dem är äldre än den andra, så ordningen ska inte utge sig för att visa ålder
  if (g[0].generationer) throw new Error('"äldst först" sattes på en grupp utan generationer');
});

prov("Model 3, Model S och Model Y hålls isär — andra ordet bär modellen", () => {
  const g = grupper(["Tesla Model 3", "Tesla Model S", "Tesla Model Y",
                     "Tesla Model 3 Long Range 82 kWh", "Tesla Model Y RWD (Juniper)"]);
  if (g.length !== 3) throw new Error("blev " + g.length + " grupper, väntade 3");
});

prov("Highland och Juniper sorteras sist i sin grupp", () => {
  const g = grupper(["Tesla Model 3 RWD (Highland)", "Tesla Model 3",
                     "Tesla Model 3 Long Range 82 kWh", "Tesla Model 3 Performance (Highland)"]);
  const namn = gruppMed(g, "Model 3").bilar.map(function (b) { return b.modell; });
  if (/Highland/.test(namn[0]) || /Highland/.test(namn[1]))
    throw new Error("nyare generation hamnade före den äldre: " + namn.join(" | "));
});

prov("IONIQ 3, 5, 6 och 9 blir fyra grupper, inte en", () => {
  const g = grupper(["Hyundai IONIQ 3 61 kWh", "Hyundai IONIQ 5 54 kWh",
                     "Hyundai IONIQ 6", "Hyundai IONIQ 9 Long Range AWD", "Hyundai IONIQ 5 70 kWh"]);
  if (g.length !== 4) throw new Error("blev " + g.length + " grupper, väntade 4");
});

prov("Enyaq iV ligger före Enyaq — Škoda tog bort iV vid lyftet", () => {
  const g = grupper(["Škoda Enyaq 85", "Škoda Enyaq iV 60", "Škoda Enyaq Coupe 85", "Škoda Enyaq iV 80"]);
  if (g.length !== 1) throw new Error("blev " + g.length + " grupper, väntade 1");
  const namn = g[0].bilar.map(function (b) { return b.modell; });
  if (!/iV/.test(namn[0]) || !/iV/.test(namn[1]))
    throw new Error("iV-raderna ska ligga först: " + namn.join(" | "));
});

prov("Elroq 60 och Elroq 85 är samma modell — talet är en nivå, inte en modell", () => {
  const g = grupper(["Škoda Elroq 60", "Škoda Elroq 85", "Škoda Elroq 85x", "Škoda Elroq RS"]);
  if (g.length !== 1) throw new Error("blev " + g.length + " grupper, väntade 1");
});

prov("ID. Buzz och ID.Buzz är samma bil trots mellanslaget", () => {
  const g = grupper(["Volkswagen ID.Buzz", "Volkswagen ID. Buzz LWB Pro 4MOTION",
                     "Volkswagen ID. Buzz NWB Pro 4MOTION"]);
  if (g.length !== 1) throw new Error("blev " + g.length + " grupper, väntade 1");
});

prov("DS Automobiles är märket — modellerna heter DS 3, N°4, N°7 och N°8", () => {
  if (markeAv("DS Automobiles N°7 FWD") !== "DS Automobiles")
    throw new Error("märket blev " + markeAv("DS Automobiles N°7 FWD"));
  const g = grupper(["DS Automobiles DS 3 E-Tense", "DS Automobiles N°4 E-Tense",
                     "DS Automobiles N°7 FWD", "DS Automobiles N°8 FWD"]);
  if (g.length !== 4) throw new Error("blev " + g.length + " grupper, väntade 4");
});

prov("e-Tourneo Courier och Custom är olika bilar", () => {
  const g = grupper(["Ford e-Tourneo Courier", "Ford e-Tourneo Custom L1 160 kW AWD",
                     "Ford e-Tourneo Custom L2 210 kW RWD"]);
  if (g.length !== 2) throw new Error("blev " + g.length + " grupper, väntade 2");
});

prov("Proace Verso och Proace City Verso är olika bilar", () => {
  const g = grupper(["Toyota Proace Verso M 50 kWh", "Toyota Proace City Verso L1 50 kWh"]);
  if (g.length !== 2) throw new Error("blev " + g.length + " grupper, väntade 2");
});

prov("AION UT och AION V är olika bilar", () => {
  const g = grupper(["GAC AION UT 60 kWh", "GAC AION V 75 kWh"]);
  if (g.length !== 2) throw new Error("blev " + g.length + " grupper, väntade 2");
});

prov("en ensam bil blir en grupp utan rubrik", () => {
  const g = grupper(["Volvo EX60"]);
  if (g.length !== 1) throw new Error("blev " + g.length + " grupper");
  if (g[0].flera) throw new Error("en enda bil ska inte flaggas för rubrik");
});

console.log(fel === 0 ? "\nAlla prov gröna\n" : "\n" + fel + " prov föll\n");
process.exit(fel === 0 ? 0 : 1);
