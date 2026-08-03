package dev.sreedaya.sprintforge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class AuthFlowIntegrationTest {
    @Autowired MockMvc mvc;

    @Test void registersAndReturnsJwt() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dev@example.com\",\"password\":\"StrongPass123!\",\"displayName\":\"Demo Developer\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("dev@example.com"));
    }

    @Test void rejectsInvalidRegistration() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"bad\",\"password\":\"short\",\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.email").exists());
    }
}
