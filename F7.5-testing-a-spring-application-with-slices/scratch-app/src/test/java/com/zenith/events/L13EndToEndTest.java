package com.zenith.events;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

// Lesson 13 — subtask S8: the ONE allowed @SpringBootTest end-to-end test.
// Real controller -> real service -> real repository -> real (embedded) database.
// Everything else in this suite is a slice; this test proves the floors connect.
@SpringBootTest
@AutoConfigureMockMvc
class L13EndToEndTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void createdEventCanBeFetchedBack() throws Exception {
        String body = """
                {
                  "name": "Winter Jazz Night",
                  "venueId": "7b4a0f8e-1f7d-4c43-9a30-2f1d7d6c5a01",
                  "salesOpenAt": "2030-01-01T10:00:00Z",
                  "startsAt": "2030-02-01T19:00:00Z",
                  "capacity": 150
                }
                """;

        MvcResult created = mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();

        String id = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(get("/events/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Winter Jazz Night"))
                .andExpect(jsonPath("$.capacity").value(150));
    }
}
