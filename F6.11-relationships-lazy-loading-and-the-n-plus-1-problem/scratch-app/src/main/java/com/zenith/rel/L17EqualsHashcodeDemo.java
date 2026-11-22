package com.zenith.rel;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

// Lesson 17: @Data's equals()/hashCode() put the database-generated id into the hash.
// The id changes on save -> a HashSet loses the very object it is holding.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l17-equals-hashcode
@Component
@Profile("l17-equals-hashcode")
class L17EqualsHashcodeDemo implements CommandLineRunner {

    private final BadNoteRepository notes;

    L17EqualsHashcodeDemo(BadNoteRepository notes) {
        this.notes = notes;
    }

    @Override
    @Transactional
    public void run(String... args) {
        BadNoteEntity note = new BadNoteEntity();
        note.setBody("remember the milk");

        System.out.println(">>> BEFORE save -----------------------------------------");
        System.out.println(">>> id:       " + note.getId());         // null
        System.out.println(">>> hashCode: " + note.hashCode());

        Set<BadNoteEntity> set = new HashSet<>();
        set.add(note);
        System.out.println(">>> added to a HashSet. set.contains(note)? " + set.contains(note));

        notes.saveAndFlush(note);   // the DATABASE assigns the id here (watch the INSERT)

        System.out.println(">>> AFTER save ------------------------------------------");
        System.out.println(">>> id:       " + note.getId());         // a real number now
        System.out.println(">>> hashCode: " + note.hashCode());      // CHANGED

        System.out.println(">>> set.contains(note)?  " + set.contains(note) + "   <-- the object is lost");
        System.out.println(">>> but is it physically still inside? iterate the set:");
        for (BadNoteEntity n : set) {
            System.out.println(">>>   iteration finds: id=" + n.getId() + ", body=" + n.getBody());
        }
    }
}
