package zenith;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// F5.11 Lesson 4: the entity - this class is the Java twin of the "event" table.
@Entity
public class Event {

    @Id
    private UUID id;                 // column: id   (the primary key)

    private String name;             // column: name
    private OffsetDateTime startsAt; // column: starts_at  (camelCase -> snake_case)
    private OffsetDateTime doorsOpenAt; // F5.11 subtask J: new field, NO migration yet - drift on purpose

    // F5.11 Lesson 9: the parent's mirror list. It adds NO column anywhere.
    // mappedBy = "event" names the FIELD on PriceTier that owns the link.
    @OneToMany(mappedBy = "event")
    private List<PriceTier> priceTiers;

    protected Event() {}             // Hibernate's door: it builds the object empty, then fills the fields

    public UUID getId() { return id; }
    public String getName() { return name; }
    public OffsetDateTime getStartsAt() { return startsAt; }
    public List<PriceTier> getPriceTiers() { return priceTiers; }

    // F5.11 Lesson 10: a setter - we change the object, and only the object.
    public void setName(String name) { this.name = name; }
}
