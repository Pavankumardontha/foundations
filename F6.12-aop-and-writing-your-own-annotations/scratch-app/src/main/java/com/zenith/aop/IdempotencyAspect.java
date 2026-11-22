package com.zenith.aop;

import com.zenith.common.Idempotent;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Lesson 10 (subtask S5): advice that can SKIP the real method.
// A tiny in-memory version of the Phase 5 idempotency interceptor.
@Aspect
@Component
public class IdempotencyAspect {

    private final Map<String, Object> storedResponses = new ConcurrentHashMap<>();

    @Around("@annotation(com.zenith.common.Idempotent)")
    public Object guard(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Idempotent idempotent = signature.getMethod().getAnnotation(Idempotent.class);
        String key = idempotent.key();

        if (storedResponses.containsKey(key)) {   // seen this key before?
            System.out.println("duplicate '" + key + "': returning stored response, skipping the method");
            return storedResponses.get(key);      // proceed() is NEVER called
        }

        Object result = joinPoint.proceed();      // first time: run the real method
        storedResponses.put(key, result);         // remember its answer
        return result;
    }
}
