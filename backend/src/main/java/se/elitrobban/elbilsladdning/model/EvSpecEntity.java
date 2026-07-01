package se.elitrobban.elbilsladdning.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "ev_spec")
public class EvSpecEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "car_name")
    private String carName;

    @Column(name = "max_ac_kw")
    private Double maxAcKw;

    @Column(name = "max_dc_kw")
    private Double maxDcKw;

    @Column(name = "battery_kwh")
    private Double batteryKwh;

    @Column(name = "range_km")
    private Integer rangeKm;

    @Column(name = "price_kr")
    private Integer priceKr;

    @Column(name = "car_type")
    private String carType;

    public Long getId()          { return id; }
    public String getCarName()   { return carName; }
    public Double getMaxAcKw()   { return maxAcKw; }
    public Double getMaxDcKw()   { return maxDcKw; }
    public Double getBatteryKwh(){ return batteryKwh; }
    public Integer getRangeKm()  { return rangeKm; }
    public Integer getPriceKr()  { return priceKr; }
    public String getCarType()   { return carType; }
}
