package com.zenith.rel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

// Lesson 16: repository for the deliberately-wrong @Data entity.
public interface BadEventRepository extends JpaRepository<BadEventEntity, UUID> {
}
