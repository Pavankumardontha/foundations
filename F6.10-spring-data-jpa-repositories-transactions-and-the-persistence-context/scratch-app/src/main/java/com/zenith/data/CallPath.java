package com.zenith.data;

// Lesson 23: prints the calls that are running right now, newest first. It keeps only your own
// classes, the proxy's handler, and the transaction step. The full list is about 30 lines.
class CallPath {

    static void print() {
        StackTraceElement[] frames = Thread.currentThread().getStackTrace();
        System.out.println(">>>    call path, newest first:");
        // frames[0] is getStackTrace itself and frames[1] is this method, so start at 2.
        for (int i = 2; i < frames.length; i++) {
            String className = frames[i].getClassName();
            if (className.startsWith("com.zenith")
                    || className.endsWith("DynamicAdvisedInterceptor")
                    || className.endsWith("TransactionInterceptor")) {
                System.out.println(">>>      " + frames[i]);
            }
        }
    }
}
