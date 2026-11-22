package com.zenith.rel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

// Spring Data writes the implementation at startup; we only declare the interface.
public interface RoleGrantRepository extends JpaRepository<RoleGrantEntity, UUID> {
}
