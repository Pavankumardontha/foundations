package com.zenith.scopes;

import org.springframework.stereotype.Service;

@Service
public class CallCounterService {
    private int callCount = 0;   // a mutable field on a bean — the subject of this whole task

    public int record() {
        callCount = callCount + 1;
        return callCount;
    }

    public int current() {
        return callCount;
    }
}
