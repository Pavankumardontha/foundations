package com.zenith.events;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Lesson 10 — subtask S5: the F6.8 error contract, proven in the web slice.
// Validation 400 with field names, domain exceptions 409/422, one error shape.
// No database anywhere: all of this is web-layer behaviour.
@WebMvcTest(EventController.class)
class L10ErrorShapeTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    EventService service;   // needed so the slice can build the controller

    @Test
    void blankNameGives400AndNamesTheField() throws Exception {
        String body = """
                {
                  "name": "",
                  "venueId": "7b4a0f8e-1f7d-4c43-9a30-2f1d7d6c5a01",
                  "salesOpenAt": "2030-01-01T10:00:00Z",
                  "startsAt": "2030-02-01T19:00:00Z",
                  "capacity": 150
                }
                """;

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.code").value("validation_failed"))
               .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    void unreadableJsonGives400WithMalformedCode() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.code").value("malformed_request"));
    }

    @Test
    void publishingWithoutPriceTierGives422() throws Exception {
        mockMvc.perform(post("/events/{id}/publish", UUID.randomUUID()))
               .andExpect(status().isUnprocessableEntity())
               .andExpect(jsonPath("$.code").value("event_not_publishable"));
    }

    @Test
    void reservingTakenSeatGives409() throws Exception {
        mockMvc.perform(post("/events/{id}/seats/{seat}", UUID.randomUUID(), "A12"))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.code").value("seat_already_reserved"))
               .andExpect(jsonPath("$.message").value("Seat A12 is already reserved."));
    }
}
