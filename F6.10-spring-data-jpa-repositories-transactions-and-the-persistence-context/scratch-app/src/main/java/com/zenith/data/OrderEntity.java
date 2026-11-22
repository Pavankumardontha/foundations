package com.zenith.data;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

// Lesson 20: one ticket order = one row of the "ticket_order" table.
@Entity
@Table(name = "ticket_order")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // the database picks the id when the row is inserted
    private Long id;                  // column: id. Long, not long, so it can be null before the insert

    private UUID eventId;             // column: event_id
    private String customerEmail;     // column: customer_email
    private int quantity;             // column: quantity

    protected OrderEntity() {}        // Hibernate builds the object empty, then fills the fields

    public OrderEntity(UUID eventId, String customerEmail, int quantity) {
        this.eventId = eventId;
        this.customerEmail = customerEmail;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getCustomerEmail() { return customerEmail; }
    public int getQuantity() { return quantity; }
}
