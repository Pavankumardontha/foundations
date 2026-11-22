package com.zenith.rel;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Lesson 18: @Data generates a setter for EVERY field, including the @Id.
// Changing the id of a MANAGED entity is forbidden - Hibernate catches it at flush.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l18-setters
@Component
@Profile("l18-setters")
class L18SettersDemo implements CommandLineRunner {

    private final BadNoteRepository notes;

    L18SettersDemo(BadNoteRepository notes) {
        this.notes = notes;
    }

    @Override
    @Transactional
    public void run(String... args) {
        BadNoteEntity note = new BadNoteEntity();
        note.setBody("original");
        notes.saveAndFlush(note);                 // now saved and MANAGED
        Long realId = note.getId();
        System.out.println(">>> saved. managed id = " + realId);

        // DANGER: @Data handed us setId(). Nothing stops us changing the primary key.
        System.out.println(">>> calling the generated setId(999L) on a managed entity ...");
        note.setId(999L);
        System.out.println(">>> in-memory id is now " + note.getId());

        System.out.println(">>> flushing (Hibernate will compare id to its snapshot) ...");
        try {
            notes.flush();
            System.out.println(">>> flush completed - no complaint?!");
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            System.out.println(">>> CRASH:   " + root.getClass().getName());
            System.out.println(">>> message: " + root.getMessage());
        }
    }
}
