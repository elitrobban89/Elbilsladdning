/*
 * Prov för prislogiken i ev-app.js.
 *
 * Kör:  node backend/src/test/js/pris-prov.js
 *
 * VARFÖR DEN FINNS. ev-app.js är 2 300 rader utan ett enda prov, och 2026-08-18 gick ett
 * "data is not defined" hela vägen till drift eftersom en utbrytning bara syntaxkollades.
 * En ReferenceError syns först när raden KÖRS — och den raden kördes bara när AI:n gett
 * ett svar, alltså aldrig i den vy jag råkade titta på.
 *
 * Prislogiken är det som borde provas först: den räknar fram kronor som visas för
 * användaren ("Full laddning ~X kr", "+Y kr jämfört med hemmaladdning", laddkalkylens
 * kostnad), och den låg förut inline på tre ställen med var sin kopia av växelkursen.
 *
 * HUR DEN LÄSER KODEN. Filen klipper ut blocket mellan markörerna PRISLOGIK BÖRJAR och
 * PRISLOGIK SLUTAR ur den riktiga ev-app.js och kör det. Ingen kopia av logiken finns
 * här — flyttas markörerna, eller byter någon namn på funktionerna, faller provet.
 */
const fs = require("fs");
const path = require("path");

const APP = path.join(__dirname, "..", "..", "main", "resources", "static", "ev-app.js");
const kalla = fs.readFileSync(APP, "utf8");

const START = "// ===== PRISLOGIK BÖRJAR";
const SLUT = "// ===== PRISLOGIK SLUTAR =====";
const i = kalla.indexOf(START);
const j = kalla.indexOf(SLUT);
if (i < 0 || j < 0) {
  console.error("Hittade inte prislogikens markörer i ev-app.js — har de flyttats eller döpts om?");
  process.exit(1);
}

// Kör blocket och plocka ut funktionerna. eval i ett prov är rätt verktyg här: poängen
// är att köra PRODUKTIONSKODEN, inte en avskrift av den.
const block = kalla.slice(i, j + SLUT.length);
const hamta = new Function(block + `
  return { EUR_SEK, HEMMA_KR_PER_KWH, tolkaLaddpris, fullLaddningKr, verkligaMil, krPerMilAv, merKostnadMotHemma };
`);
const P = hamta();

let fel = 0;
function prov(namn, fn) {
  try { fn(); console.log("  ok    " + namn); }
  catch (e) { fel++; console.log("  FEL   " + namn + "\n          " + e.message); }
}
function lika(fick, vantat, vad) {
  const a = JSON.stringify(fick), b = JSON.stringify(vantat);
  if (a !== b) throw new Error((vad || "") + " fick " + a + ", väntade " + b);
}

console.log("\nPrislogik ur ev-app.js\n");

// ---- tolkaLaddpris ----
prov("svenskt pris med komma", () => lika(P.tolkaLaddpris("4,75 kr/kWh"), { krPerKwh: 4.75, gratis: false, varEur: false }));
prov("svenskt pris med punkt", () => lika(P.tolkaLaddpris("4.75 kr/kWh"), { krPerKwh: 4.75, gratis: false, varEur: false }));
prov("heltal utan decimal", () => lika(P.tolkaLaddpris("5 kr/kWh"), { krPerKwh: 5, gratis: false, varEur: false }));

prov("varEur flaggas sa stationskortet kan visa omraknat pris", () => {
  // Stationskortet visar rakraften i kronor NAR kallan angett euro, annars ratexten.
  // Foll 2026-08-18: refaktoreringen tog bort isEur men lamnade kvar anvandningen.
  if (P.tolkaLaddpris("0,45 EUR/kWh").varEur !== true) throw new Error("EUR-pris flaggades inte");
  if (P.tolkaLaddpris("4,75 kr/kWh").varEur !== false) throw new Error("svenskt pris flaggades som EUR");
  if (P.tolkaLaddpris("").varEur !== false) throw new Error("tom strang flaggades som EUR");
});

prov("EUR räknas om med EUR_SEK, inte med ett hårdkodat tal", () => {
  const r = P.tolkaLaddpris("0,45 EUR/kWh");
  lika(r.krPerKwh, 0.45 * P.EUR_SEK, "omräkningen");
  if (P.EUR_SEK !== 11.5) throw new Error("kursen har ändrats — uppdatera provet medvetet");
});

prov("gratis flaggas, oavsett skiftläge och språk", () => {
  if (!P.tolkaLaddpris("Gratis").gratis) throw new Error("svenska 'Gratis' missades");
  if (!P.tolkaLaddpris("FREE parking").gratis) throw new Error("engelska 'FREE' missades");
});

prov("tomt eller okänt pris ger null, inte 0", () => {
  lika(P.tolkaLaddpris("").krPerKwh, null, "tom sträng");
  lika(P.tolkaLaddpris("okänt pris").krPerKwh, null, "text utan tal");
  lika(P.tolkaLaddpris(null).krPerKwh, null, "null");
  lika(P.tolkaLaddpris(undefined).krPerKwh, null, "undefined");
});

prov("plockar FÖRSTA talet, inte ett hopklistrat", () => {
  // "1,50-3,50 kr/kWh" ska ge 1,50 - den gamla regexen /[\d,.]+/ svalde bindestrecket
  // aldrig men matchade "1,50" korrekt; provet låser att beteendet inte glider.
  lika(P.tolkaLaddpris("1,50-3,50 kr/kWh").krPerKwh, 1.5);
});

// ---- fullLaddningKr ----
prov("full laddning avrundas till hel krona", () => lika(P.fullLaddningKr(64, 4.75, false), 304));
prov("gratis station ger null, inte 0 kr", () => lika(P.fullLaddningKr(64, 4.75, true), null));
prov("saknat batteri eller pris ger null", () => {
  lika(P.fullLaddningKr(null, 4.75, false), null, "utan batteri");
  lika(P.fullLaddningKr(64, null, false), null, "utan pris");
});

// ---- verkligaMil ----
prov("verklig räckvidd är WLTP minus 15 %, i mil", () => lika(P.verkligaMil(475), 40));
prov("saknad räckvidd ger null", () => lika(P.verkligaMil(null), null));

// ---- krPerMilAv ----
prov("kr/mil räknas ur full laddning och verkliga mil", () => lika(P.krPerMilAv(304, 40), 8));
prov("kr/mil ger null när underlaget saknas", () => {
  lika(P.krPerMilAv(null, 40), null, "utan kostnad");
  lika(P.krPerMilAv(304, null), null, "utan mil");
  lika(P.krPerMilAv(304, 0), null, "noll mil får aldrig ge division med noll");
});

// ---- merKostnadMotHemma ----
prov("merkostnad mot hemmaladdning", () => {
  // 64 kWh hemma = 64 * 2,00 = 128 kr. Station 304 kr => 176 kr dyrare.
  lika(P.merKostnadMotHemma(64, 304), 176);
});
prov("billigare än hemma ger null, aldrig ett negativt tal", () => {
  lika(P.merKostnadMotHemma(64, 100), null);
});
prov("hemmapriset kommer från konstanten", () => {
  if (P.HEMMA_KR_PER_KWH !== 2.0) throw new Error("hemmapriset har ändrats — uppdatera provet medvetet");
});

// ---- gränsen mot noll ----
prov("noll kr/kWh behandlas som okänt, inte som gratis-0", () => {
  // Ett pris på 0 i datat är nästan alltid saknad uppgift, inte en gratis laddare.
  // Gratis ska komma från ORDET gratis/free, och det provas för sig ovan.
  lika(P.fullLaddningKr(64, 0, false), null);
});

console.log(fel === 0 ? "\nAlla prov gröna\n" : "\n" + fel + " prov föll\n");
process.exit(fel === 0 ? 0 : 1);
