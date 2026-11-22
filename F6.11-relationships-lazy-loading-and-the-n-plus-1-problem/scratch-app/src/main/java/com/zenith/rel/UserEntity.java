package com.zenith.rel;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// The Java class that stands for the "app_user" table. One object = one row.
@Entity
@Table(name = "app_user")
public class UserEntity {

    @Id
    private UUID id;              // column: id (the primary key)

    private String email;         // column: email
    private String displayName;   // column: display_name

    // Lesson 5: a user has many roles AND a role has many users.
    // Neither table can hold the other's key, so the pairs live in the
    // join table "user_role". This side names that table and its two
    // key columns, which makes this side the owning side.
    // Lesson 7: LAZY is already the collection default; written out anyway (project rule).
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_role",                                  // the join table
            joinColumns = @JoinColumn(name = "user_id"),         // my key column in it
            inverseJoinColumns = @JoinColumn(name = "role_id"))  // the other key column
    private Set<RoleEntity> roles = new HashSet<>();

    protected UserEntity() {}     // Hibernate builds the object empty, then fills the fields

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public Set<RoleEntity> getRoles() { return roles; }
}
