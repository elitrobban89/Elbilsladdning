/*
 * Drift-vakt: våra kopior mot dem CarAdvice faktiskt serverar.
 *
 * Kör:  node backend/src/test/js/drift-prov.js
 *
 * ev-app.js och ev-splash.js finns i TVÅ repon och måste vara identiska:
 *   · CarAdvice      src/main/resources/static/          ← SERVERAS därifrån
 *   · Elbilsladdning backend/src/main/resources/static/  ← TESTAS här
 *
 * Uppdelningen har ett skäl åt vardera hållet. De serveras av CarAdvice därför att den
 * här tjänsten ligger på Renders gratisnivå och somnar efter ~15 min — och när sidans
 * kod hämtades härifrån blockerades hela gränssnittet av kallstarten, inte bara datan.
 * De testas här därför att proven redan bor här och klipper ut riktig kod ur filerna.
 *
 * En manuell kopiering som glöms bort ger TYST drift. Det hände i systerprojektet samma
 * dag som uppdelningen infördes: en vaktfix lades i den serverade kopian medan testerna
 * fortsatte köra den gamla. Det här provet gör skillnaden hörbar i stället för tyst.
 *
 * Hoppas över när nätet inte svarar: ett rött prov för att Render är nere säger ingenting
 * om koden, och en vakt som larmar av fel skäl slutar man läsa.
 */
const fs = require("fs");
const path = require("path");

const VARD = "https://caradvice.onrender.com";
const FILER = ["ev-app.js", "ev-splash.js"];
const STATIC = path.join(__dirname, "..", "..", "main", "resources", "static");

const norm = (s) => s.split(String.fromCharCode(13, 10)).join(String.fromCharCode(10));

let fel = 0;
let hoppade = 0;

(async () => {
  for (const f of FILER) {
    let serverad;
    try {
      const res = await fetch(VARD + "/" + f, { signal: AbortSignal.timeout(30000) });
      if (!res.ok) { console.log("  hoppar  " + f + " — CarAdvice svarade " + res.status); hoppade++; continue; }
      serverad = await res.text();
    } catch (e) {
      console.log("  hoppar  " + f + " — kunde inte nå CarAdvice (" + e.message + ")");
      hoppade++;
      continue;
    }
    const lokal = fs.readFileSync(path.join(STATIC, f), "utf8");
    if (norm(lokal) === norm(serverad)) {
      console.log("  ok      " + f + " är identisk med den serverade");
    } else {
      console.log("  FEL     " + f + " skiljer sig från den CarAdvice serverar");
      console.log("          kopiera till CarAdvice src/main/resources/static/" + f + " och deploya");
      fel++;
    }
  }

  if (fel === 0 && hoppade === 0) console.log("\nAlla prov gröna");
  else if (fel === 0) console.log("\nInget fel — " + hoppade + " hoppade (nätet svarade inte)");
  else console.log("\n" + fel + " prov FÖLL");
  process.exit(fel === 0 ? 0 : 1);
})();
