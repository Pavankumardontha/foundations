package com.zenith.testing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FreshBasketTest {

    private List<String> basket;

    @BeforeEach
    void createFreshBasket() {
        basket = new ArrayList<>();
        System.out.println("setup: new empty basket created");
    }

    @Test
    void addingOneItemGivesSizeOne() {
        basket.add("apple");
        System.out.println("test A: basket size is " + basket.size());
        assertEquals(1, basket.size());
    }

    @Test
    void addingTwoItemsGivesSizeTwo() {
        basket.add("bread");
        basket.add("milk");
        System.out.println("test B: basket size is " + basket.size());
        assertEquals(2, basket.size());
    }
}
