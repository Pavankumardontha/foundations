package com.zenith.events;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Lesson 8 — and subtask S2, complete: the same 404 assertion as L4,
// now in the web slice. EventService is a Mockito mock placed into the context.
@WebMvcTest(EventController.class)
class L8WebSlice404Test {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    EventService service;

    @Test
    void returns404WithErrorBodyWhenEventDoesNotExist() throws Exception {
        when(service.byId(any())).thenThrow(new EventNotFoundException());

        mockMvc.perform(get("/events/{id}", UUID.randomUUID()))
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.status").value(404))
               .andExpect(jsonPath("$.code").value("event_not_found"))
               .andExpect(jsonPath("$.message").value("Event not found"));
    }
}
