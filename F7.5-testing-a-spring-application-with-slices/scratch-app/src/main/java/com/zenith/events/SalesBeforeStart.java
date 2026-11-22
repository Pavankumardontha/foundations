package com.zenith.events;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target(ElementType.TYPE)                              // may sit on a class or record
@Retention(RetentionPolicy.RUNTIME)                    // visible to the engine while running
@Constraint(validatedBy = SalesBeforeStartValidator.class)  // the class that does the checking
public @interface SalesBeforeStart {

    // Every constraint annotation must offer these three members.
    String message() default "sales must open before the event starts";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
