package com.zenith.rel;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

// Lesson 6: the join table, grown up. One object = one granted role,
// with data about the granting itself.
@Entity
@Table(name = "role_grant")
public class RoleGrantEntity {

    @Id
    private UUID id;                   // its OWN primary key now

    // The two halves of the old pair, each a plain @ManyToOne (Lesson 1 pattern).
    // Lesson 7: LAZY written out on both (project rule).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private RoleEntity role;

    // Data about the LINK itself - impossible to place in a @ManyToMany.
    private String grantedBy;          // column: granted_by
    private OffsetDateTime grantedAt;  // column: granted_at

    protected RoleGrantEntity() {}     // Hibernate builds the object empty, then fills the fields

    public UUID getId() { return id; }
    public UserEntity getUser() { return user; }
    public RoleEntity getRole() { return role; }
    public String getGrantedBy() { return grantedBy; }
    public OffsetDateTime getGrantedAt() { return grantedAt; }
}
