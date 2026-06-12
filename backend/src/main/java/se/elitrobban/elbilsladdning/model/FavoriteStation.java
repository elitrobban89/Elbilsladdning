package se.elitrobban.elbilsladdning.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ev_favorites")
public class FavoriteStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(nullable = false)
    private String name;

    private String address;
    private double lat;
    private double lon;

    @Column(name = "max_eff_kw")
    private double maxEffKw;

    @Column(name = "connector_type")
    private String connectorType;

    private String operator;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { createdAt = LocalDateTime.now(); }

    public Long getId()                  { return id; }
    public String getUserId()            { return userId; }
    public void setUserId(String v)      { userId = v; }
    public String getName()              { return name; }
    public void setName(String v)        { name = v; }
    public String getAddress()           { return address; }
    public void setAddress(String v)     { address = v; }
    public double getLat()               { return lat; }
    public void setLat(double v)         { lat = v; }
    public double getLon()               { return lon; }
    public void setLon(double v)         { lon = v; }
    public double getMaxEffKw()          { return maxEffKw; }
    public void setMaxEffKw(double v)    { maxEffKw = v; }
    public String getConnectorType()     { return connectorType; }
    public void setConnectorType(String v){ connectorType = v; }
    public String getOperator()          { return operator; }
    public void setOperator(String v)    { operator = v; }
    public LocalDateTime getCreatedAt()  { return createdAt; }
}
