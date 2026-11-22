package com.zenith.events;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

// Lesson 3: the whole application, plus MockMvc to push fake HTTP
// requests through the real web machinery. No port, no network.
@SpringBootTest
@AutoConfigureMockMvc
class L3MockMvcTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void returns404WhenEventDoesNotExist() throws Exception {
        // The database is empty, so any random id is unknown.
        // Real service, real lookup, real exception, real handler: a real 404.
        mockMvc.perform(get("/events/{id}", UUID.randomUUID()))
               .andExpect(status().isNotFound());
    }
}
