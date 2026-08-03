package dev.sreedaya.sprintforge;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectFlowIntegrationTest {
    private static final String PASSWORD = "StrongPass123!";

    @Autowired
    MockMvc mvc;

    @Test
    void createsProjectWorkItemAndBoardSummary() throws Exception {
        Session owner = register("owner@example.org", "Taylor Reed");
        String projectId = createProject(owner.token(), "Delivery Board", "DLVRY");

        mvc.perform(post("/api/v1/projects/{projectId}/work-items", projectId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Review release checklist",
                                  "status": "IN_PROGRESS",
                                  "priority": "HIGH",
                                  "assigneeId": "%s"
                                }
                                """.formatted(owner.userId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Review release checklist"))
                .andExpect(jsonPath("$.assigneeId").value(owner.userId()));

        mvc.perform(get("/api/v1/projects/{projectId}/work-items/summary", projectId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.counts.IN_PROGRESS").value(1));

        mvc.perform(get("/api/v1/projects/{projectId}/work-items", projectId)
                        .param("status", "IN_PROGRESS")
                        .param("priority", "HIGH")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mvc.perform(get("/api/v1/projects/{projectId}/audit-events", projectId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].action").value("WORK_ITEM_CREATED"));
    }

    @Test
    void enforcesMembershipAndOwnerOnlyOperations() throws Exception {
        Session owner = register("project-owner@example.org", "Morgan Lee");
        Session colleague = register("colleague@example.org", "Jordan Kim");
        String projectId = createProject(owner.token(), "Operations", "OPS42");

        mvc.perform(get("/api/v1/projects/{projectId}", projectId)
                        .header("Authorization", bearer(colleague.token())))
                .andExpect(status().isNotFound());

        mvc.perform(post(
                        "/api/v1/projects/{projectId}/members/{email}",
                        projectId,
                        "colleague@example.org")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(2));

        mvc.perform(get("/api/v1/projects/{projectId}", projectId)
                        .header("Authorization", bearer(colleague.token())))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/v1/projects/{projectId}/archive", projectId)
                        .header("Authorization", bearer(colleague.token())))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/v1/projects/{projectId}/archive", projectId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    private Session register(String email, String displayName) throws Exception {
        String request = """
                {
                  "email": "%s",
                  "password": "%s",
                  "displayName": "%s"
                }
                """.formatted(email, PASSWORD, displayName);
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn();
        String response = result.getResponse().getContentAsString();
        return new Session(
                JsonPath.read(response, "$.token"),
                JsonPath.read(response, "$.user.id"));
    }

    private String createProject(String token, String name, String key) throws Exception {
        String request = """
                {"name": "%s", "key": "%s", "description": "Shared delivery plan"}
                """.formatted(name, key);
        MvcResult result = mvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Session(String token, String userId) {}
}
