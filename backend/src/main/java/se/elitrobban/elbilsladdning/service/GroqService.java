package se.elitrobban.elbilsladdning.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import se.elitrobban.elbilsladdning.model.CarSpec;
import se.elitrobban.elbilsladdning.model.StationDto;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GroqService {

    private static final String MODEL     = "openai/gpt-oss-20b";
    private static final int CHAT_MAX_HISTORY = 8;
    private static final long   CACHE_TTL = 30 * 60 * 1000L;

    @Value("${groq.api.key}")
    private String apiKey;

    // Överstyrbar i tester — pekas mot en lokal stubbserver
    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqUrl;

    // Groq får ett EGET, längre tak: den skriver text och behöver mer tid än en
    // uppslagstjänst, men inte oändligt. Utan tak kan ett tyst AI-anrop hålla hela
    // stationssökningen öppen i all evighet (uppmätt 2026-08-30: nio minuter).
    private final RestClient http = HttpTimeouts.restClient(Duration.ofSeconds(5), Duration.ofSeconds(30));
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private record CacheEntry(GroqResult result, long timestamp) {}
    private final Map<String, CacheEntry> recommendCache = new ConcurrentHashMap<>();
    private volatile long quotaExceededUntil = 0;

    public record GroqResult(String recommendation, String funFact) {}

    public boolean isQuotaExceeded() {
        return System.currentTimeMillis() < quotaExceededUntil;
    }

    @SuppressWarnings("unchecked")
    public String chat(List<Map<String, String>> history, List<CarSpec> cars, String stationContext) {
        if (isQuotaExceeded()) return "AI-assistenten är tillfälligt otillgänglig — dagsgränsen är nådd. Försök igen imorgon!";

        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "system", "content", byggSysPrompt(history, cars, stationContext)));
        List<Map<String, String>> trimmed = history.size() > CHAT_MAX_HISTORY
                ? history.subList(history.size() - CHAT_MAX_HISTORY, history.size()) : history;
        trimmed.forEach(m -> messages.add(Map.of("role", (Object) m.get("role"), "content", (Object) m.get("content"))));

        Map<String, Object> body = Map.of(
                "model", MODEL, "max_tokens", 800, "temperature", 0.7,
                "reasoning_effort", "low",
                "messages", messages);
        try {
            Map<String, Object> resp = http.post()
                    .uri(groqUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            var choices = (List<Map<String, Object>>) resp.get("choices");
            var msg     = (Map<String, Object>) choices.get(0).get("message");
            quotaExceededUntil = 0;
            return (String) msg.get("content");
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429)
                quotaExceededUntil = System.currentTimeMillis() + parseRetryMs(e.getResponseBodyAsString());
            return "AI-assistenten är tillfälligt otillgänglig — dagsgränsen är nådd. Försök igen imorgon!";
        } catch (Exception e) {
            return "Tyvärr kunde jag inte svara just nu. Försök igen!";
        }
    }

    /** Tal utan onödig decimal: 60.0 blir "60", 77.4 blir "77.4". */
    private static String tal(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** Så många nämnda bilar som får plats i prompten. */
    static final int MAX_NAMNDA_BILAR = 6;

    /** Så många av de senaste ANVÄNDARraderna som genomsöks efter bilnamn. */
    private static final int NAMNDA_BAKAT = 3;

    /**
     * Systemprompt plus all kontext, byggd på ETT ställe.
     *
     * <p>Låg förut som två identiska kopior i {@link #chat} och {@link #chatStream}. Streaming är
     * frontendens normalväg och den icke-strömmande är reservvägen, alltså exakt den kombination
     * där en glidning mellan kopiorna märks sist — reservvägen körs bara när den första fallerat.
     */
    private String byggSysPrompt(List<Map<String, String>> history, List<CarSpec> cars, String stationContext) {
        String sysPrompt = buildChatSystemPrompt(cars);
        String namnda = namndaBilarBlock(history, cars);
        if (!namnda.isEmpty()) sysPrompt += "\n\n" + namnda;
        if (stationContext != null && !stationContext.isBlank())
            sysPrompt += "\n\nAktuella laddstationer i sökningen:\n" + stationContext;
        return sysPrompt;
    }

    /**
     * Specarna för de bilar användaren nämnt VID NAMN i sina senaste rader.
     *
     * <p><b>Varför blocket behövs.</b> Kontexten bär bara den bil som är VALD i väljaren, och
     * prompten i övrigt bara tre topp-5-listor. Frågar man om en bil utan att välja den fanns
     * alltså inga siffror alls, och 2026-08-29 svarade assistenten "1,2-1,3 timmar" på hur länge
     * en Volvo EX60 laddar från 0 % med 50 kW — räknat på ett batteri den läst ur NAMNET. Att
     * lägga alla 520 rader i prompten är inget alternativ (Groqs tak är 8 000 tokens/minut), så
     * uppslagningen sker per fråga och bara det som efterfrågats följer med.
     *
     * <p><b>De tre senaste användarraderna, inte bara den sista</b>, eftersom följdfrågan nästan
     * aldrig upprepar bilnamnet ("och med 50 kW då?"). Assistentens EGNA svar genomsöks inte —
     * annars hade en bil den själv råkat nämna dragit in sina specar och bekräftat sig själv.
     */
    String namndaBilarBlock(List<Map<String, String>> history, List<CarSpec> cars) {
        List<CarSpec> traffar = namndaBilar(senasteAnvandartext(history), cars);
        if (traffar.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("NÄMNDA BILAR (uppslagna i databasen — använd DESSA siffror):\n");
        for (CarSpec c : traffar) {
            sb.append("  ").append(c.name()).append(": ").append(tal(c.batteryKwh())).append(" kWh batteri");
            if (c.rangeKm() > 0) sb.append(", ").append(c.rangeKm()).append(" km WLTP (~")
                    .append(Math.round(c.rangeKm() * 0.85)).append(" km verklig)");
            // "DC max 0 kW" läser som en trasig mätning; de tre AC-bara bilarna säger det rakt ut.
            sb.append(c.maxDcKw() > 0 ? ", DC max " + (int) c.maxDcKw() + " kW" : ", ingen snabbladdning");
            if (c.maxAcKw() > 0) sb.append(", AC max ").append(tal(c.maxAcKw())).append(" kW");
            if (c.priceKr() > 0) sb.append(", ").append(c.priceKr() / 1000).append(" tkr");
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String senasteAnvandartext(List<Map<String, String>> history) {
        if (history == null) return "";
        StringBuilder sb = new StringBuilder();
        int tagna = 0;
        for (int i = history.size() - 1; i >= 0 && tagna < NAMNDA_BAKAT; i--) {
            Map<String, String> m = history.get(i);
            if (m == null || !"user".equals(m.get("role"))) continue;
            sb.append(' ').append(m.get("content") == null ? "" : m.get("content"));
            tagna++;
        }
        return sb.toString();
    }

    /**
     * Bilarna vars namn står i texten — hela namnet, eller modellbeteckningen utan märke.
     *
     * <p><b>Träffarna grupperas per modellfamilj och plockas VARVAT</b>, inte som en enda lista.
     * Uppmätt mot de riktiga 520 namnen: "vad är skillnaden mellan Škoda Enyaq och en ID.4?" ger
     * <b>12</b> träffar och "Polestar 2 jämfört med Tesla Model 3" ger <b>8</b> — en rak lista hade
     * fyllt taket med den ena bilens varianter och kapat bort den andra bilen ur en fråga som
     * uttryckligen gäller båda. Varvningen ger i stället tre av varje.
     *
     * <p>Inom en familj går <b>hela namnet före modellbeteckningen</b> och kortaste namnet före
     * långa. Det första ledet skyddar den bil frågan faktiskt gäller: "berätta om Audi Q4 e-tron"
     * drar även in {@code Audi e-tron GT quattro} på ordet e-tron, och utan rankningen kunde den
     * ha trängt undan bilen som stavades ut. Det andra ledet gör basmodellen till den man menar
     * när man bara säger "MG4" — nio rader matchar, och {@code MG4 Long Range} är rimligare svar
     * än {@code MG4 Urban Premium Long Range}.
     */
    static List<CarSpec> namndaBilar(String text, List<CarSpec> cars) {
        String hay = normText(text);
        if (hay.isBlank() || cars == null) return List.of();
        java.util.Map<String, List<CarSpec>> familjer = new java.util.LinkedHashMap<>();
        java.util.Set<String> stavadeUt = new java.util.HashSet<>();
        for (CarSpec c : cars) {
            String namn = normText(c.name()).trim();
            if (namn.isEmpty()) continue;
            String nyckel = modellNyckel(namn);
            boolean helaNamnet   = hay.contains(" " + namn + " ");
            boolean baraModellen = !nyckel.isEmpty() && hay.contains(" " + nyckel + " ");
            if (!helaNamnet && !baraModellen) continue;
            if (helaNamnet) stavadeUt.add(c.name());
            familjer.computeIfAbsent(nyckel.isEmpty() ? namn : nyckel, k -> new java.util.ArrayList<>()).add(c);
        }
        if (familjer.isEmpty()) return List.of();
        List<List<CarSpec>> ordnade = new java.util.ArrayList<>(familjer.values());
        for (List<CarSpec> familj : ordnade)
            familj.sort(java.util.Comparator.comparing((CarSpec c) -> !stavadeUt.contains(c.name()))
                    .thenComparingInt(c -> c.name().length()));
        // Familjen som stavades ut i frågan går först. Sorteringen är stabil, så familjer utan
        // utstavad träff behåller inbördes ordning i stället för att kastas om godtyckligt.
        ordnade.sort(java.util.Comparator.comparing(
                f -> f.stream().noneMatch(c -> stavadeUt.contains(c.name()))));

        List<CarSpec> ut = new java.util.ArrayList<>();
        for (int varv = 0; ut.size() < MAX_NAMNDA_BILAR; varv++) {
            boolean nagotKvar = false;
            for (List<CarSpec> familj : ordnade) {
                if (varv >= familj.size()) continue;
                nagotKvar = true;
                if (ut.size() < MAX_NAMNDA_BILAR) ut.add(familj.get(varv));
            }
            if (!nagotKvar) break;
        }
        return List.copyOf(ut);
    }

    /**
     * Texten nedkokt till gemener, utan diakriter och med allt annat än a-z0-9 som mellanslag.
     *
     * <p>Samma behandling på BÅDA sidor, vilket är hela poängen: "Škoda" blir "skoda", "ID.7" blir
     * "id 7" och "e-tron?" blir "e tron" oavsett om strängen kom ur databasen eller från en
     * användare som skriver utan skiftläge och med frågetecken.
     */
    private static String normText(String s) {
        String t = java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return " " + t + " ";
    }

    /**
     * Modellord som inte identifierar en bil på egen hand — de förekommer på tvärs av märken.
     * Utan listan hade "model" dragit in varenda Tesla och "range" alla Long Range-varianter.
     */
    private static final java.util.Set<String> GENERISKA_MODELLORD = java.util.Set.of(
            "model", "long", "range", "standard", "single", "twin", "motor", "performance",
            "premium", "comfort", "urban", "extended", "electric", "sportback", "avant",
            "coupe", "plug", "pro", "plus", "max", "life", "style", "business", "edition");

    /**
     * Modellbeteckningen ur ett radnamn: märkesordet bort, och sedan så många ord som krävs för
     * att beteckningen ska stå för sig själv. Tom sträng när ingen sådan finns.
     *
     * <p>Kravet är formulerat på vad som gör en beteckning UNIK, inte på ordlängd:
     * <ul>
     *   <li>siffra OCH bokstav räcker från två tecken — {@code i3}, {@code q4}, {@code ex30};</li>
     *   <li>rena bokstäver kräver tre tecken och att ordet inte är generiskt — {@code zoe},
     *       {@code kona}, {@code taycan};</li>
     *   <li>en ensam siffra duger aldrig, så {@code Polestar 2} kräver att märket står med —
     *       annars hade "ladda till 2 procent" dragit in bilen.</li>
     * </ul>
     * {@code Tesla Model 3} går igenom först på TVÅ ord, eftersom {@code model} är generiskt.
     *
     * <p><b>Märkesordet hoppas normalt över, men inte alltid.</b> {@code MG4 Long Range} börjar med
     * modellen — märket är MG och "MG4" är bilen — så en fråga om "MG4" hade annars inte träffat en
     * enda av de nio MG4-raderna, eftersom ingen av dem bär en distinkt beteckning EFTER det ordet
     * ("long", "range", "standard" och "premium" är alla generiska). Första ordet duger därför som
     * nyckel när det självt bär både siffra och bokstav. Inventerat över hela tabellen: exakt
     * {@code mg4} och {@code mg5} uppfyller det, alltså just de två fallen regeln finns för —
     * {@code volvo} och {@code tesla} saknar siffra och hade annars dragit in hela märkets utbud.
     */
    private static String modellNyckel(String normaltNamn) {
        String[] ord = normaltNamn.split(" ");
        if (ord.length == 0) return "";
        if (ord[0].length() >= 2 && harSiffraOchBokstav(ord[0])) return ord[0];
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < ord.length && i <= 3; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(ord[i]);
            String s = sb.toString();
            if (s.length() >= 2 && harSiffraOchBokstav(s)) return s;
            if (s.length() >= 3 && !GENERISKA_MODELLORD.contains(ord[1])) return s;
        }
        return "";
    }

    private static boolean harSiffraOchBokstav(String s) {
        return s.matches(".*\\d.*") && s.matches(".*[a-z].*");
    }

    String buildChatSystemPrompt(List<CarSpec> cars) {
        var sb = new StringBuilder();
        sb.append("""
Du är en assistent för en EV-laddningsapp i Sverige.
Du svarar ENDAST på frågor om elbilar, laddning, räckvidd, laddstationer och bilresor.

Om användaren frågar om något annat svarar du:
"Det kan jag inte hjälpa med — jag är specialiserad på elbilar och laddning."

Svara alltid på svenska. Svara kortfattat (max 3–4 meningar) men konkret och specifik med siffror.
Om någon frågar om elbilsköp och inte angett budget, fråga efter det.

PRENUMERATION (svara på detta om användaren frågar om prenumeration, pris, vad som ingår, abonnemang):
- Prenumerationen kostar 49 kr/månad.
- I prenumerationen ingår tre tjänster: AI Bilrådgivning (hjälp att välja rätt elbil), AI EV Laddassistent (denna chatbot) samt Bränslekostnadsberäkning (jämför elbil mot bensin/diesel).
- Svara med: "Prenumerationen kostar 49 kr/månad och inkluderar tre tjänster: AI Bilrådgivning, AI EV Laddassistent samt Bränslekostnadsberäkning."

BILLIGASTE LADDNING (svara på detta om användaren frågar var de laddar billigast, hur de sparar pengar på laddning etc.):
- Rekommendera ALLTID hemmaladdning som det billigaste alternativet: ~1,50–3,50 kr/kWh beroende på elavtal.
- Om stationskontext finns: nämn också den billigaste publik stationen i listan med namn, pris och avstånd.
- Exempel på svar: "Billigast är att ladda hemma (~1,50–3,50 kr/kWh). Närmaste billiga publika alternativ är [station] ([pris], [avstånd] km bort)."

KONTEXTUELLA FÖLJDFRÅGOR (använd alltid den skickade kontexten för att svara relevant):
- Om kontexten visar "Vald bil: X" — svara alltid utifrån den bilens specs (batteri, DC, räckvidd, pris).
- Om kontexten visar "Laddtidskalkylator" — du vet vad användaren räknat på (från/till%, tid, kostnad, räckvidd). Svara på följdfrågor som "är det snabbt?", "är det dyrt?", "kan jag nå Y med det?".
- Om kontexten visar "Snabbaste DC (topp 3)" eller "Längst räckvidd (topp 3)" — du kan svara på frågor om rankingarna, varför en viss bil är bäst, vad den kostar jämfört med alternativ.
- Om kontexten visar "Faktaruta visad" — du känner till vilket faktum som visades och kan fördjupa det.
- Om kontexten visar "AI-rekommendation" — det är rekommendationen användaren redan sett; du kan förklara/fördjupa den.

SIFFROR OM EN VISS BIL — batteri i kWh, räckvidd, DC-effekt, laddtid:
- Ta dem ENDAST ur "NÄMNDA BILAR" (bilarna du blivit tillfrågad om, uppslagna i databasen),
  ur kontexten ("Vald bil: ...", "Laddtidskalkylator: ...") eller ur BILDATA nedan.
- Härled ALDRIG batteriets storlek ur modellnamnet. EX60, ID.4, iX3 och Q4 är NAMN, inte kWh.
  Uppmätt 2026-08-29: utan kontext svarade du "1,2-1,3 timmar" för en Volvo EX60 på en 50 kW-laddare,
  räknat på ett påhittat 60 kWh-batteri. Bilen har 112 kWh och tar 2 timmar 14 minuter — med kontext
  svarade du rätt. Ett rimligt klingande tal ur namnet är alltså det enda felet som uppstår här.
- Står bilen under "NÄMNDA BILAR" har du dess riktiga siffror även om ingen bil är vald i väljaren.
  Svara på dem direkt, och säg inte att du saknar data.
- Har du inte bilens siffror någonstans: säg det rakt ut och be användaren välja bilmodellen i
  väljaren högst upp. Gissa inte, och räkna inte på ett antaget batteri.

RUTTPLANERING (om stationskontexten innehåller en "PLANERAD RUTT"-sektion ska du använda den informationen):
- Om användaren frågar om sin rutt, laddstoppar, om de klarar sträckan eller vilket stopp som är bäst, svara baserat på ruttkontexten.
- Berätta vilket laddningsstopp som rekommenderas och varför (t.ex. strategisk placering halvvägs, snabbast laddning, bäst pris).
- Om kontexten innehåller raden "Bilen klarar sträckan utan laddningsstopp" MÅSTE du inleda svaret med en tydlig, uttrycklig bekräftelse av just detta innan du ger några tips — t.ex. "Din [bilnamn] klarar hela sträckan till [destination] utan laddstopp." Nämn gärna sträckan i km. Ge SEDAN korta råd (t.ex. ladda fullt hemma innan avfärd). Hitta aldrig på ett laddstopp när kontexten säger att inget behövs.
- Om det finns flera stopp, förklara hur de är utplacerade längs rutten.
- Proaktivt tips: om kontexten visar att en rutt är planerad men användaren inte frågat om den, kan du kort nämna "Jag ser att du planerat en rutt till [destination] — vill du veta mer om laddstopparna?"

BUDGET-REGLER (följ dessa exakt):
- Om budgeten är under 200 000 kr: förklara direkt att nya elbilar sällan finns under det priset i Sverige,\s
  och rekommendera begagnade alternativ som Renault Zoe (50–90 tkr), Nissan Leaf (60–120 tkr),\s
  MG ZS EV (80–140 tkr), VW e-Golf (70–110 tkr). Säg aldrig en ny bil som kostar mer än budgeten.
- Om budgeten är 200 000–300 000 kr: visa billigaste nya (Dacia Spring ~180 tkr, MG4 ~260 tkr,\s
  VW ID.3 ~290 tkr) OCH nämn att begagnade kan ge mer för pengarna.
- Rekommendera ALDRIG en bil till mer än 1,3x budgeten utan att tydligt säga att den är över budget.
- Om du föreslår en begagnad bil, säg "begagnad [modell] (~XX–YY tkr)" med prisintervall.

""")
          // Talet var hårdkodat till 73 medan tabellen bar 520 rader. En prompt som säger fel
          // antal är en prompt modellen inte kan lita på — och den listar ändå bara topp 5.
          .append("BILDATA (" + cars.size() + " modeller i databasen, topplistor nedan):\n\n");

        sb.append("Snabbaste DC-laddning:\n");
        cars.stream().filter(c -> c.maxDcKw() > 0 && c.priceKr() > 0)
            .sorted((a, b) -> Double.compare(b.maxDcKw(), a.maxDcKw())).limit(5)
            .forEach(c -> sb.append(String.format("  %s: %d kW DC, %d tkr, %s kWh batteri%n",
                c.name(), (int) c.maxDcKw(), c.priceKr() / 1000, tal(c.batteryKwh()))));

        sb.append("\nLängst räckvidd (WLTP):\n");
        cars.stream().filter(c -> c.rangeKm() > 0 && c.priceKr() > 0)
            .sorted((a, b) -> Integer.compare(b.rangeKm(), a.rangeKm())).limit(5)
            .forEach(c -> sb.append(String.format("  %s: %d km, %d tkr, %s kWh batteri%n",
                c.name(), c.rangeKm(), c.priceKr() / 1000, tal(c.batteryKwh()))));

        sb.append("\nBäst värde (km per 100 000 kr):\n");
        cars.stream().filter(c -> c.rangeKm() > 0 && c.priceKr() > 0)
            .sorted((a, b) -> Double.compare(
                b.rangeKm() * 100_000.0 / b.priceKr(),
                a.rangeKm() * 100_000.0 / a.priceKr())).limit(5)
            .forEach(c -> sb.append(String.format("  %s: %d km/100tkr, %d tkr%n",
                c.name(), (int)(c.rangeKm() * 100_000.0 / c.priceKr()), c.priceKr() / 1000)));

        sb.append("""

RECENSIONER & UTMÄRKELSER (svenska källor — citera dessa när relevant):

Teknikens Värld:
  Kia EV6: "Bästa elbil under 600 000 kr" – vann årets elbildstest 2022
  Hyundai IONIQ 6: "Räckviddsmästaren" – slog rekord i deras förbrukningstest
  MG4: "Bäst i test budget-elbil 2023" – prisvärd och rymlig
  Tesla Model 3: Konsekvent topplacering, utmärkt driftekonomi
  Volvo EX30: Lyfts fram som "kompakt och prisvärd" för stadskörning
  BMW i4: "Bäst köregenskaper bland elbilar" i körtester
  Polestar 2: "Premiumkänsla till rimligare pris än tyska konkurrenter"

Vi Bilägare:
  Tesla Model Y: "Mest praktiska elbilen för barnfamiljen"
  Kia EV6: Rekommenderas som "bästa köp" i mellanklassen
  Volkswagen ID.3: "Stabilt och tryggt val" för kompaktsegmentet
  Dacia Spring: "Billigast att äga och ladda" i sin klass
  Hyundai IONIQ 5: "Bäst i test" stor elbil – betyg 5/5 interiör

Råd & Rön:
  Tesla Model 3: Rekommenderas för lägst driftkostnad per mil
  MG4: Prisvärd med bra ägarbetyg från svenska ägare

Instruktion: När du rekommenderar en bil, nämn kort om den fått bra recensioner från dessa källor.
Säg alltid vilken källa du refererar till (t.ex. "Enligt Teknikens Värld...").
Hitta INTE på recensioner som inte finns i listan ovan.
""");

        return sb.toString();
    }

    public InputStream chatStream(List<Map<String, String>> history, List<CarSpec> cars, String stationContext) throws Exception {
        if (isQuotaExceeded()) throw new RuntimeException("AI-assistenten är tillfälligt otillgänglig — dagsgränsen är nådd. Försök igen imorgon!");
        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "system", "content", byggSysPrompt(history, cars, stationContext)));
        List<Map<String, String>> trimmedStream = history.size() > CHAT_MAX_HISTORY
                ? history.subList(history.size() - CHAT_MAX_HISTORY, history.size()) : history;
        trimmedStream.forEach(m -> messages.add(Map.of("role", (Object) m.get("role"), "content", (Object) m.get("content"))));

        Map<String, Object> body = Map.of(
                "model", MODEL, "max_tokens", 800, "temperature", 0.7, "stream", true,
                "reasoning_effort", "low",
                "messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(groqUrl))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() == 401) throw new RuntimeException("AI-tjänsten är inte korrekt konfigurerad.");
        if (response.statusCode() == 429) {
            String body429 = new String(response.body().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            quotaExceededUntil = System.currentTimeMillis() + parseRetryMs(body429);
            throw new RuntimeException("AI-assistenten är tillfälligt otillgänglig — dagsgränsen är nådd. Försök igen imorgon!");
        }
        if (response.statusCode() != 200) throw new RuntimeException("AI-tjänsten svarade med fel " + response.statusCode() + ".");
        return response.body();
    }

    public GroqResult recommend(CarSpec car, List<StationDto> stations, String costComparison) {
        String key = car.name() + stations.stream().limit(3).map(StationDto::name).reduce("", (a, b) -> a + "|" + b);
        CacheEntry hit = recommendCache.get(key);
        if (hit != null && System.currentTimeMillis() - hit.timestamp() < CACHE_TTL) return hit.result();

        if (System.currentTimeMillis() < quotaExceededUntil) return buildFallback(car, stations);

        String userPrompt = buildPrompt(car, stations, costComparison);
        Map<String, Object> body = Map.of(
                "model", MODEL,
                "max_tokens", 800,
                "temperature", 0.8,
                "reasoning_effort", "low",
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "Du är en expert på elbilsladdning i Sverige. " +
                                "Svara alltid på svenska. Svara i exakt detta format:\n" +
                                "REKOMMENDATION: [2–3 meningar med konkret råd om vilken station som passar bäst. Hitta aldrig på priser.]\n" +
                                "VISSTE DU ATT: [ett kort, intressant faktum om bilen eller elbilsladdning i allmänhet. Max 1 mening.]"),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = http.post()
                    .uri(groqUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            @SuppressWarnings("unchecked")
            var choices = (List<Map<String, Object>>) resp.get("choices");
            var message = (Map<String, Object>) choices.get(0).get("message");
            String content = (String) message.get("content");

            String recommendation = extractSectionCI(content, "REKOMMENDATION:", "VISSTE DU ATT:");
            String funFact        = extractSectionCI(content, "VISSTE DU ATT:", null);
            quotaExceededUntil = 0;
            GroqResult result = new GroqResult(recommendation, funFact);
            if (recommendCache.size() > 200) recommendCache.clear();
            recommendCache.put(key, new CacheEntry(result, System.currentTimeMillis()));
            return result;

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                quotaExceededUntil = System.currentTimeMillis() + parseRetryMs(e.getResponseBodyAsString());
            }
            GroqResult fallback = buildFallback(car, stations);
            recommendCache.put(key, new CacheEntry(fallback, System.currentTimeMillis()));
            return fallback;
        } catch (Exception e) {
            return buildFallback(car, stations);
        }
    }

    GroqResult buildFallback(CarSpec car, List<StationDto> stations) {
        if (stations.isEmpty()) return new GroqResult("Inga laddstationer hittades i närheten.", null);
        StationDto best = stations.get(0);
        String rec = String.format("%s (%.1f km, %d kW %s) passar bäst för din %s.",
                best.name(), best.distanceKm(), (int) best.maxEffKw(), best.connectorType(), car.name());
        return new GroqResult(rec, null);
    }

    long parseRetryMs(String body) {
        try {
            Matcher m = Pattern.compile("try again in ([\\d]+m[\\d.]+s|[\\d.]+s)").matcher(body);
            if (!m.find()) return 15 * 60 * 1000L;
            String t = m.group(1);
            Matcher min = Pattern.compile("(\\d+)m").matcher(t);
            Matcher sec = Pattern.compile("([\\d.]+)s").matcher(t);
            int minutes = min.find() ? Integer.parseInt(min.group(1)) : 0;
            double seconds = sec.find() ? Double.parseDouble(sec.group(1)) : 0;
            return (long) ((minutes * 60 + seconds) * 1000);
        } catch (Exception e) {
            return 15 * 60 * 1000L;
        }
    }

    /** Case-insensitive section extraction — handles AI variations like "Visste du att:" vs "VISSTE DU ATT:". */
    String extractSectionCI(String text, String startMarker, String endMarker) {
        String upper = text.toUpperCase();
        int start = upper.indexOf(startMarker.toUpperCase());
        if (start < 0) return null;
        start += startMarker.length();
        int end = endMarker != null
                ? upper.indexOf(endMarker.toUpperCase(), start)
                : text.length();
        if (end < 0) end = text.length();
        return text.substring(start, end).strip();
    }

    String buildPrompt(CarSpec car, List<StationDto> stations, String costComparison) {
        var sb = new StringBuilder();
        sb.append("Bil: ").append(car.name())
          .append(" (DC max ").append((int) car.maxDcKw())
          .append(" kW, AC max ").append((int) car.maxAcKw()).append(" kW)\n\n");
        sb.append("Topp laddstationer:\n");

        int n = Math.min(5, stations.size());
        for (int i = 0; i < n; i++) {
            StationDto s = stations.get(i);
            String bp   = s.bestPrice();
            String pris = bp != null ? "Pris: " + bp : "Pris: okänt";
            sb.append(i + 1).append(". ").append(s.name())
              .append(" – ").append(String.format("%.1f", s.distanceKm())).append(" km")
              .append(" – ").append((int) s.maxEffKw()).append(" kW effektivt")
              .append(" (").append(s.connectorType()).append(")")
              .append(" – ").append(pris)
              .append("\n");
        }

        if (costComparison != null) {
            sb.append("\nKostnadsjämförelse med andra elbilar:\n").append(costComparison).append("\n");
        }

        sb.append("\nVilken station rekommenderar du och varför? ")
          .append("Om priset är okänt, nämn det och be användaren kolla operatörens app.");
        return sb.toString();
    }
}
