import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Lesson 4 — the finished declaration, exactly as subtask S2 asks for it.
// @Retention(RUNTIME): the label survives into the running program (Lesson 3).
// @Target(METHOD):     the label may only be written on methods (this lesson).
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Timed { }
