package com.zenith.events;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

// Lesson 4 — and subtask S1, complete:
// "GET /events/{id} returns 404 for an unknown id", proven with the
// whole application context, and the error body checked field by field.
@SpringBootTest
@AutoConfigureMockMvc
class L4FullContext404Test {

    @Autowired
    MockMvc mockMvc;

    @Test
    void returns404WithErrorBodyWhenEventDoesNotExist() throws Exception {
        mockMvc.perform(get("/events/{id}", UUID.randomUUID()))
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.status").value(404))
               .andExpect(jsonPath("$.code").value("event_not_found"))
               .andExpect(jsonPath("$.message").value("Event not found"));
    }
}
