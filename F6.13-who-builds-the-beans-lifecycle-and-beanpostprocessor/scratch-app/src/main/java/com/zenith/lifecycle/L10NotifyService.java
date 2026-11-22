package com.zenith.lifecycle;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Lesson 10: self-invocation — the raw object calling itself, past the proxy.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l10-selfinvocation
@Component
@Profile("l10-selfinvocation")
class L10NotifyService {

    @Transactional
    public void saveNote() {
        System.out.println(">>> saveNote: this is " + this.getClass().getSimpleName()
                + ", transaction open? "
                + TransactionSynchronizationManager.isActualTransactionActive());
    }

    public void saveNoteTwice() {
        System.out.println(">>> saveNoteTwice: calling this.saveNote() — a self-invocation");
        this.saveNote();   // plain Java call on the raw object — no proxy on this path
    }
}
