package com.zenith.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Lesson 10 (subtask S5). The key has NO default on purpose:
// forgetting an idempotency key must be a compile error, not a silent gap.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Idempotent {

    String key();
}
