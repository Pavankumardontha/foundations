package zenith;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import java.util.UUID;

// F5.11 Lesson 8: the child side of the relationship.
// Many price tiers point at one event - so the field lives HERE, on the many side.
@Entity
public class PriceTier {

    @Id
    private UUID id;        // column: id

    private String name;    // column: name

    @ManyToOne
    private Event event;    // column: event_id (field name + "_id") - the child owns the link

    protected PriceTier() {}

    public UUID getId() { return id; }
    public String getName() { return name; }
    public Event getEvent() { return event; }
}
