package se.elitrobban.elbilsladdning.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Senaste lyckat lästa videolistan ur Alriks Bilars kanalflöde, som JSON. Exakt en rad
 * ({@link #ENDA_ID}).
 *
 * <p>Samma skäl som {@link VroomTopplistaSnapshot}: tjänsten somnar på gratisplanen, och är
 * YouTubes flöde nere vid uppvakningen ska kortet visa förra listan i stället för ingenting.
 *
 * @author Robert Andersson Kopler
 */
@Entity
@Table(name = "bilvideor")
public class BilvideoSnapshot {

    public static final long ENDA_ID = 1L;

    @Id
    private Long id;

    @Column(columnDefinition = "text", nullable = false)
    private String json;

    @Column(name = "sparad_vid", nullable = false)
    private Instant sparadVid;

    public BilvideoSnapshot() {}

    public BilvideoSnapshot(String json, Instant sparadVid) {
        this.id = ENDA_ID;
        this.json = json;
        this.sparadVid = sparadVid;
    }

    public Long getId()          { return id; }
    public String getJson()      { return json; }
    public Instant getSparadVid(){ return sparadVid; }
}
