package com.skelar.defuse;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"defuse.auth.enabled=true", "defuse.auth.customer-password=test-customer-pass", "defuse.auth.admin-password=test-admin-pass"})
@AutoConfigureMockMvc
class AuthChatTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired com.skelar.defuse.store.InMemoryStore store;
    @BeforeEach void reset() { store.reset(); }
    String login(String email, String pass) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("email", email, "password", pass))))
                .andExpect(status().isOk()).andReturn();
        return "Bearer " + mapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }
    @Test void enforcesRoleAndSessionAndLogout() throws Exception {
        mvc.perform(get("/api/tickets")).andExpect(status().isUnauthorized());
        String customer = login("customer@calmly.demo", "test-customer-pass");
        mvc.perform(get("/api/tickets").header("Authorization", customer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/chat").header("Authorization", customer)).andExpect(status().isOk());
        mvc.perform(post("/api/chat/T01/offer/accept").header("Authorization", customer)).andExpect(status().isNotFound());
        var other = store.addTicket("cus_0002", "other customer's ticket", "chat");
        mvc.perform(post("/api/chat/" + other.id + "/offer/accept").header("Authorization", customer)).andExpect(status().isNotFound());
        String admin = login("admin@calmly.demo", "test-admin-pass");
        mvc.perform(get("/api/tickets").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").header("Authorization", customer)).andExpect(status().isOk());
        mvc.perform(get("/api/chat").header("Authorization", customer)).andExpect(status().isUnauthorized());
    }
    @Test void customerIdentityComesFromSessionNotBody() throws Exception {
        String customer = login("customer@calmly.demo", "test-customer-pass");
        mvc.perform(post("/api/chat").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Need help\",\"customer_id\":\"cus_0002\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").exists());
        mvc.perform(get("/api/chat").header("Authorization", customer)).andExpect(status().isOk()).andExpect(jsonPath("$[0].text").value("Need help"));
    }
    @Test void adminReplyAppearsInCustomerHistory() throws Exception {
        String customer = login("customer@calmly.demo", "test-customer-pass");
        String admin = login("admin@calmly.demo", "test-admin-pass");
        var sent = mvc.perform(post("/api/chat").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Please help with something unclear\"}"))
                .andExpect(status().isOk()).andReturn();
        String id = mapper.readTree(sent.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(post("/api/tickets/" + id + "/human-reply").header("Authorization", customer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Customer tries to impersonate operator\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/tickets/" + id + "/human-reply").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"We will review this for you.\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("resolved"));
        var history = mvc.perform(get("/api/chat").header("Authorization", customer)).andExpect(status().isOk()).andReturn();
        assertThat(mapper.readTree(history.getResponse().getContentAsString()).toString()).contains("We will review this for you.");
        mvc.perform(get("/api/analytics/diagnostics").header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved.human").isNumber()).andExpect(jsonPath("$.synthetic_segments").isArray());
    }

}
