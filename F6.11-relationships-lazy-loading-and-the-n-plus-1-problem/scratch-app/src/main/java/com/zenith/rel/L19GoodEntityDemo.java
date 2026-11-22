package com.zenith.rel;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

// Lesson 19: the SAME scenario that broke in Lesson 17 - now it works, because the key
// is stable from construction. A HashSet keeps the object across a save.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l19-good-entity
@Component
@Profile("l19-good-entity")
class L19GoodEntityDemo implements CommandLineRunner {

    private final GoodNoteRepository notes;

    L19GoodEntityDemo(GoodNoteRepository notes) {
        this.notes = notes;
    }

    @Override
    @Transactional
    public void run(String... args) {
        GoodNoteEntity note = new GoodNoteEntity("remember the milk");

        System.out.println(">>> BEFORE save -----------------------------------------");
        System.out.println(">>> id:       " + note.getId());         // already a real UUID
        System.out.println(">>> hashCode: " + note.hashCode());

        Set<GoodNoteEntity> set = new HashSet<>();
        set.add(note);
        System.out.println(">>> added to a HashSet. set.contains(note)? " + set.contains(note));

        notes.saveAndFlush(note);   // save does NOT change the id - we assigned it ourselves

        System.out.println(">>> AFTER save ------------------------------------------");
        System.out.println(">>> id:       " + note.getId());         // identical
        System.out.println(">>> hashCode: " + note.hashCode());      // identical
        System.out.println(">>> set.contains(note)?  " + set.contains(note) + "   <-- still found");
        System.out.println(">>> toString (scalars only, safe to log): " + note);
    }
}
