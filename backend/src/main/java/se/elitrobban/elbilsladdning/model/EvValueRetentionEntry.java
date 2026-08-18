package se.elitrobban.elbilsladdning.model;

import jakarta.persistence.*;

/**
 * Hur mycket en elbil av årsmodell 2021 har kvar av sitt nypris idag.
 *
 * <p><b>Två källor, och de gör olika saker.</b> {@code newPriceKr} är nypriset för årsmodellen,
 * hämtat ur Kvdbils och Bilpriser artikel "Så håller 10 vanliga elbilar sitt värde"
 * (publicerad 2024-01-22). Ett nypris för en gammal årsmodell är ett historiskt faktum som
 * inte åldras — därför är den siffran seedad i kod och inte skrapad. {@code medianPriceKr}
 * är dagens marknadspris, räknat på Blocket vid varje synk.
 *
 * <p><b>Varför inte artikelns egen "pris idag".</b> Den var färsk i januari 2024 och är det
 * inte längre: mätt 2026-08-18 hade Volkswagen ID.4 gått från artikelns 82 % kvar till
 * <b>55 %</b>, och Volvo XC40 P8 från 73 % till 43 %. Genom att bara ärva nypriset och räkna
 * om resten själva blir siffran färskare än källan den bygger på.
 *
 * <p><b>{@code adCount} är inte pynt.</b> Medianen är bara värd något med underlag bakom sig,
 * och antalet annonser varierar kraftigt mellan modeller (11 för Renault Zoe, 43 för Volvo
 * XC40 vid första mätningen). Frontend visar inte rader under {@code MIN_ANNONSER}.
 */
@Entity
@Table(name = "ev_value_retention")
public class EvValueRetentionEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Visningsnamn, t.ex. "Nissan Leaf e+ 62 kWh". */
    private String model;

    /** Årsmodellen mätningen gäller — samma år som nypriset avser. */
    @Column(name = "model_year")
    private Integer modelYear;

    /** Nypris för årsmodellen i kronor (Kvdbil/Bilpriser). */
    @Column(name = "new_price_kr")
    private Integer newPriceKr;

    /** Medianpris bland dagens annonser i kronor, eller null när underlaget var för tunt. */
    @Column(name = "median_price_kr")
    private Integer medianPriceKr;

    /** Billigaste annonsen i kronor — komplement till medianen, aldrig grunden för procenten. */
    @Column(name = "cheapest_price_kr")
    private Integer cheapestPriceKr;

    /** Antal annonser medianen vilar på. */
    @Column(name = "ad_count")
    private Integer adCount;

    /** Andel av nypriset som är kvar, avrundat till heltalsprocent. */
    @Column(name = "retention_pct")
    private Integer retentionPct;

    public EvValueRetentionEntry() {}

    public EvValueRetentionEntry(String model, Integer modelYear, Integer newPriceKr,
                                 Integer medianPriceKr, Integer cheapestPriceKr,
                                 Integer adCount, Integer retentionPct) {
        this.model = model;
        this.modelYear = modelYear;
        this.newPriceKr = newPriceKr;
        this.medianPriceKr = medianPriceKr;
        this.cheapestPriceKr = cheapestPriceKr;
        this.adCount = adCount;
        this.retentionPct = retentionPct;
    }

    public Long getId()                { return id; }
    public String getModel()           { return model; }
    public Integer getModelYear()      { return modelYear; }
    public Integer getNewPriceKr()     { return newPriceKr; }
    public Integer getMedianPriceKr()  { return medianPriceKr; }
    public Integer getCheapestPriceKr(){ return cheapestPriceKr; }
    public Integer getAdCount()        { return adCount; }
    public Integer getRetentionPct()   { return retentionPct; }
}
