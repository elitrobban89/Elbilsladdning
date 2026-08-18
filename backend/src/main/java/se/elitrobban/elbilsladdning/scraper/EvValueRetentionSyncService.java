package se.elitrobban.elbilsladdning.scraper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.elitrobban.elbilsladdning.model.EvValueRetentionEntry;
import se.elitrobban.elbilsladdning.repository.EvValueRetentionRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Räknar fram hur mycket tio vanliga elbilar har kvar av sitt nypris.
 *
 * <p><b>Nypriserna är seedade, marknadspriset skrapas.</b> Nypriset för en årsmodell 2021 är
 * ett historiskt faktum som inte ändras, så det står i {@link #MODELLER} med Kvdbil och
 * Bilpriser som källa. Vad bilen kostar <i>idag</i> ändras hela tiden och hämtas från Blocket
 * vid varje körning.
 *
 * <p><b>Därför blir siffran färskare än källan.</b> Kvdbils artikel är från 2024-01-22 och dess
 * egen "pris idag"-kolumn har åldrats: mätt 2026-08-18 hade Volkswagen ID.4 gått från artikelns
 * 82 % kvar till <b>55 %</b>, Nissan Leaf e+ från 56 % till <b>41 %</b> och Audi e-tron 55 från
 * 57 % till <b>38 %</b>. Vi ärver bara det som inte kan bli fel och räknar om resten själva.
 *
 * <p><b>En modell utan tillräckligt underlag skrivs inte.</b> Blocket kapar svaret vid 50
 * annonser och årsfiltret plus milgränsen tar bort en stor del av dem, så antalet varierar
 * kraftigt mellan modeller. Under {@link #MIN_ANNONSER} är medianen inte värd att visa, och
 * raden lämnas då orörd med sitt förra värde i stället för att skrivas över med brus.
 */
@Service
public class EvValueRetentionSyncService {

    private static final Logger log = LoggerFactory.getLogger(EvValueRetentionSyncService.class);

    /** Årsmodellen hela listan gäller — samma år som nypriserna nedan avser. */
    static final int ARSMODELL = 2021;

    /**
     * Så många annonser krävs för att medianen ska skrivas.
     *
     * <p>Fem är lågt satt med flit: alternativet till en tunn median är ingen rad alls, och en
     * median på fem annonser är fortfarande betydligt ärligare än ett enskilt pris. Antalet
     * följer med ut i API:t så att den som läser kan väga siffran själv.
     */
    static final int MIN_ANNONSER = 5;

    /** Källan för nypriserna, utskriven i API-svaret så den aldrig tappas på vägen till UI:t. */
    public static final String NYPRIS_KALLA =
            "Nypris enligt Kvdbil och Bilpriser (kvd.se, 2024-01-22)";

    /**
     * De tio modellerna ur Kvdbils artikel "Så håller 10 vanliga elbilar sitt värde".
     *
     * <p>{@code sokord} är fritexten till Blocket och {@code trimOrd} det som måste finnas i
     * annonsens {@code model_specification} för att rätt version ska räknas — utan det blandas
     * 40 kWh-Leafen in bland 62 kWh, och procenten jämförs då mot fel nypris.
     */
    record Modell(String visningsnamn, String sokord, String trimOrd, int nyprisKr) {}

    static final List<Modell> MODELLER = List.of(
            // Trimorden är VALDA UR DATAT 2026-08-18, inte gissade. De matchas mot
            // model_specification, där versionen faktiskt står ("62 kWh N-Connecta",
            // "s 120 Ah Comfort Advanced", "Pro Performance 204hk"). Ett ord som inte
            // förekommer ger noll träffar och tyst bortfall — kontrollera mot skarpa
            // annonser innan du ändrar något här.
            new Modell("Audi e-tron 55 quattro",     "Audi e-tron",         "55",         970_000),
            new Modell("Nissan Leaf e+ 62 kWh",      "Nissan Leaf",         "62",         461_500),
            new Modell("Tesla Model 3 Long Range",   "Tesla Model 3",       "long range", 629_170),
            new Modell("Renault Zoe 52 kWh",         "Renault Zoe",         null,         366_990),
            new Modell("BMW i3s 120 Ah",             "BMW i3",              "120 ah",     439_000),
            new Modell("Kia e-Niro 64 kWh",          "Kia e-Niro",          "64",         487_900),
            new Modell("Volkswagen ID.4 Pro 77 kWh", "Volkswagen ID.4",     "pro",        524_900),
            // XC40 föll till fyra annonser när laddhybriderna sorterades bort och hoppas
            // därmed över av MIN_ANNONSER. Raden står kvar med flit: utbudet varierar, och
            // den dag det finns underlag ska bilen med.
            new Modell("Volvo XC40 P8 AWD Recharge", "Volvo XC40 Recharge", null,         699_000),
            new Modell("MG ZS EV 45 kWh",            "MG ZS EV",            null,         359_000),
            new Modell("Seat Mii electric 36,8 kWh", "Seat Mii",            null,         264_900)
    );

    private final BlocketUsedPriceClient blocket;
    private final EvValueRetentionRepository repo;

    public EvValueRetentionSyncService(BlocketUsedPriceClient blocket, EvValueRetentionRepository repo) {
        this.blocket = blocket;
        this.repo = repo;
    }

    /**
     * Hämtar prisbilden för varje modell och ersätter tabellen.
     *
     * <p>Tabellen ersätts bara när minst en modell gav ett dugligt svar. Är Blocket nere ska
     * gårdagens siffror stå kvar — en tom fyndlista är sämre än en dagsfärsk, och en tom
     * tabell efter en misslyckad körning hade dessutom sett ut som att inga fynd finns.
     */
    @Transactional
    public Map<String, Object> syncNow() {
        List<EvValueRetentionEntry> rader = new ArrayList<>();
        int tunna = 0;

        for (Modell m : MODELLER) {
            var pris = blocket.prisbildFor(m.sokord(), ARSMODELL, m.trimOrd());
            if (pris.antal() < MIN_ANNONSER || pris.medianKr() == null) {
                tunna++;
                log.info("värdetapp: {} hoppas över — bara {} dugliga annonser", m.visningsnamn(), pris.antal());
                continue;
            }
            int kvarPct = (int) Math.round(pris.medianKr() * 100.0 / m.nyprisKr());
            rader.add(new EvValueRetentionEntry(
                    m.visningsnamn(), ARSMODELL, m.nyprisKr(),
                    pris.medianKr(), pris.billigastKr(), pris.antal(), kvarPct));
            log.info("värdetapp: {} — nypris {} kr, median {} kr ({} annonser), {} % kvar",
                    m.visningsnamn(), m.nyprisKr(), pris.medianKr(), pris.antal(), kvarPct);
        }

        Map<String, Object> resultat = new LinkedHashMap<>();
        if (rader.isEmpty()) {
            log.warn("värdetapp: ingen modell gav underlag — behåller föregående tabell");
            resultat.put("status", "FEL");
            resultat.put("orsak", "inga dugliga annonser för någon modell");
            resultat.put("behöll", repo.count());
            return resultat;
        }

        repo.deleteAllInBatch();
        repo.saveAll(rader);

        resultat.put("status", "OK");
        resultat.put("modeller", rader.size());
        resultat.put("hoppadeÖver", tunna);
        resultat.put("årsmodell", ARSMODELL);
        resultat.put("källa", NYPRIS_KALLA);
        return resultat;
    }
}
