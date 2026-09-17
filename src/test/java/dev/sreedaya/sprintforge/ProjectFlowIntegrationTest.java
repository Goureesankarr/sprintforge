package dev.sreedaya.sprintforge;

import com.jayway.jsonpath.JsonPath;
import dev.sreedaya.sprintforge.notification.NotificationOutbox;
import dev.sreedaya.sprintforge.notification.NotificationOutboxRepository;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    @Autowired
    NotificationOutboxRepository outbox;

    @Test
    void createsProjectWorkItemAndBoardSummary() throws Exception {
        Session owner = register("owner@example.org", "Taylor Reed");
        String projectId = createProject(owner.token(), "Delivery Board", "DLVRY");
        String sprintId = createSprint(owner.token(), projectId);

        mvc.perform(post("/api/v1/projects/{projectId}/work-items", projectId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Review release checklist",
                                  "status": "IN_PROGRESS",
                                  "priority": "HIGH",
                                  "assigneeId": "%s",
                                  "sprintId": "%s"
                                }
                                """.formatted(owner.userId(), sprintId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Review release checklist"))
                .andExpect(jsonPath("$.assigneeId").value(owner.userId()))
                .andExpect(jsonPath("$.sprintId").value(sprintId));

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
                .andExpect(jsonPath("$.content.length()").value(3))
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

        assertThat(outbox.countByStatusIn(List.of(NotificationOutbox.Status.PENDING)))
                .isPositive();

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

    @Test
    void supportsAuthorizedWorkItemDiscussionsWithSoftDeletion() throws Exception {
        Session owner = register("discussion-owner@example.org", "Avery Singh");
        Session member = register("discussion-member@example.org", "Riley Shah");
        Session outsider = register("discussion-outsider@example.org", "Casey Rao");
        String projectId = createProject(owner.token(), "Incident Review", "INCDNT");

        mvc.perform(post(
                        "/api/v1/projects/{projectId}/members/{email}",
                        projectId,
                        "discussion-member@example.org")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk());

        String workItemId = createWorkItem(owner.token(), projectId);
        MvcResult created = mvc.perform(post(
                                "/api/v1/projects/{projectId}/work-items/{workItemId}/comments",
                                projectId,
                                workItemId)
                        .header("Authorization", bearer(member.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "I reproduced this in the staging environment."}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorId").value(member.userId()))
                .andExpect(jsonPath("$.authorName").value("Riley Shah"))
                .andReturn();
        String commentId = JsonPath.read(
                created.getResponse().getContentAsString(), "$.id");

        mvc.perform(patch(
                                "/api/v1/projects/{projectId}/work-items/{workItemId}/comments/{commentId}",
                                projectId,
                                workItemId,
                                commentId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "Owners must not rewrite another member's comment."}
                                """))
                .andExpect(status().isForbidden());

        mvc.perform(patch(
                                "/api/v1/projects/{projectId}/work-items/{workItemId}/comments/{commentId}",
                                projectId,
                                workItemId,
                                commentId)
                        .header("Authorization", bearer(member.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "I reproduced this in staging and attached the logs."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body")
                        .value("I reproduced this in staging and attached the logs."));

        mvc.perform(get(
                                "/api/v1/projects/{projectId}/work-items/{workItemId}/comments",
                                projectId,
                                workItemId)
                        .header("Authorization", bearer(outsider.token())))
                .andExpect(status().isNotFound());

        mvc.perform(delete(
                                "/api/v1/projects/{projectId}/work-items/{workItemId}/comments/{commentId}",
                                projectId,
                                workItemId,
                                commentId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isNoContent());

        mvc.perform(get(
                                "/api/v1/projects/{projectId}/work-items/{workItemId}/comments",
                                projectId,
                                workItemId)
                        .header("Authorization", bearer(member.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        mvc.perform(get("/api/v1/projects/{projectId}/audit-events", projectId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("COMMENT_DELETED"))
                .andExpect(jsonPath("$.content[0].entityType").value("COMMENT"));
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

    private String createSprint(String token, String projectId) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/projects/{projectId}/sprints", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Release 1",
                                  "goal": "Prepare the first stable release",
                                  "startDate": "2026-08-03",
                                  "endDate": "2026-08-17"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createWorkItem(String token, String projectId) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/projects/{projectId}/work-items", projectId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Investigate intermittent timeout",
                                  "status": "IN_PROGRESS",
                                  "priority": "HIGH"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Session(String token, String userId) {}
}
