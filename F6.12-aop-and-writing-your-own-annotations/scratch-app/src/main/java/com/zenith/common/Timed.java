package com.zenith.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// The mark (subtask S2). RUNTIME: visible to the running program, so the aspect can see it.
// METHOD: may only be written on methods.
// It lives in com.zenith.common - a DIFFERENT package from the aspect, on purpose:
// that is what makes the fully-qualified-name rule in the pointcut matter.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Timed {

    // Lesson 9: a slot for extra information. The name "value" is special:
    // it allows the shorthand @Timed("checkout"). The default makes it optional.
    String value() default "";
}
