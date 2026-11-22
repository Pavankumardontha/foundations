package com.zenith.testing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationOrderTest {

    static String boom() {
        System.out.println("boom() is computing the argument");
        throw new IllegalStateException("exploded while computing the argument");
    }

    static void receive(String s) {
        System.out.println("receive() started");
    }

    @Test
    void aLambdaIsNotRunWhenItIsCreated() {
        Runnable wrapped = () -> boom();
        System.out.println("lambda created and passed around - boom() has not run");
    }

    @Test
    void theAssertionRunsTheLambdaInsideItsOwnTryCatch() {
        assertThatThrownBy(() -> boom())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exploded");
    }
}
