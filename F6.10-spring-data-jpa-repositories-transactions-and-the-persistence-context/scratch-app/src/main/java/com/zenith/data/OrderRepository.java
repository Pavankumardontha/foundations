package com.zenith.data;

import org.springframework.data.jpa.repository.JpaRepository;

// Lesson 20: the repository for ticket orders.
// <OrderEntity, Long> = what it stores, and the type of that entity's @Id field.
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
}
