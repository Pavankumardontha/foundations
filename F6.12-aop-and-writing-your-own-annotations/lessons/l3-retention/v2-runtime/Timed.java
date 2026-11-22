import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// Version 2 — retention set to RUNTIME: the label survives into the running program.
@Retention(RetentionPolicy.RUNTIME)
public @interface Timed { }
