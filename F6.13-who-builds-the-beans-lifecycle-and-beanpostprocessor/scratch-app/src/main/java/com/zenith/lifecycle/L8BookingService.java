package com.zenith.lifecycle;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Lesson 8: a service WITH @Transactional — the context will hold a stand-in for it.
@Component
@Profile("l8-transactional")
class L8BookingService {

    private final JdbcClient db;

    L8BookingService(JdbcClient db) {
        this.db = db;
    }

    @Transactional
    public int reserveSeat() {
        boolean open = TransactionSynchronizationManager.isActualTransactionActive();
        System.out.println(">>> inside reserveSeat: transaction open? " + open);
        return db.sql("SELECT 1").query(Integer.class).single();
    }
}
