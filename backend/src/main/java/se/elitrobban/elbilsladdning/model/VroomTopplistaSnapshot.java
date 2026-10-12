package se.elitrobban.elbilsladdning.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Senaste lyckat lästa Vroom-topplistan, som JSON. Exakt en rad ({@link #ENDA_ID}).
 *
 * <p>Finns för att tjänsten somnar på gratisplanen: minnescachen är tom vid varje uppvakning,
 * och var YouTubes kanalflöde nere just då (helt globalt 2026-10-12, även Googles egna kanaler
 * gav 404) visade appen ingen topplista alls fast den hade haft en dagen innan.
 *
 * @author Robert Andersson Kopler
 */
@Entity
@Table(name = "vroom_topplista")
public class VroomTopplistaSnapshot {

    public static final long ENDA_ID = 1L;

    @Id
    private Long id;

    @Column(columnDefinition = "text", nullable = false)
    private String json;

    @Column(name = "sparad_vid", nullable = false)
    private Instant sparadVid;

    public VroomTopplistaSnapshot() {}

    public VroomTopplistaSnapshot(String json, Instant sparadVid) {
        this.id = ENDA_ID;
        this.json = json;
        this.sparadVid = sparadVid;
    }

    public Long getId()          { return id; }
    public String getJson()      { return json; }
    public Instant getSparadVid(){ return sparadVid; }
}
