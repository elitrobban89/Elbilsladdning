package se.elitrobban.elbilsladdning.model;

import jakarta.persistence.*;

@Entity
@Table(name = "ev_sales_rank")
public class EvSalesRankEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer rank;

    private String model;

    private Integer units;

    @Column(name = "price_kr")
    private Integer priceKr;

    @Column(name = "period_label")
    private String periodLabel;

    public EvSalesRankEntry() {}

    public EvSalesRankEntry(Integer rank, String model, Integer units, Integer priceKr, String periodLabel) {
        this.rank = rank;
        this.model = model;
        this.units = units;
        this.priceKr = priceKr;
        this.periodLabel = periodLabel;
    }

    public Long getId()           { return id; }
    public Integer getRank()      { return rank; }
    public String getModel()      { return model; }
    public Integer getUnits()     { return units; }
    public Integer getPriceKr()   { return priceKr; }
    public String getPeriodLabel(){ return periodLabel; }
}
