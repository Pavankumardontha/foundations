package com.zenith.aop;

import com.zenith.common.Timed;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

// The aspect (subtask S3): the one class holding the around-code for the whole app.
@Aspect
@Component
public class TimingAspect {

    // Pointcut: methods annotated with @Timed - ALWAYS the fully qualified name.
    @Around("@annotation(com.zenith.common.Timed)")
    public Object time(ProceedingJoinPoint joinPoint) throws Throwable {
        // Lesson 9 (subtask S4): reach the annotation and read its value.
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Timed timed = signature.getMethod().getAnnotation(Timed.class);
        String label = timed.value().isEmpty()
                ? signature.getMethod().getName()   // no value given: use the method name
                : timed.value();                    // value given: use it

        long start = System.nanoTime();

        Object result = joinPoint.proceed();   // run the real method

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println(label + " took " + elapsedMs + " ms");
        return result;
    }
}
