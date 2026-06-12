package com.rubberduck.domain.document;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class DocumentFlowTest {

    @Autowired
    private WebApplicationContext context;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void uploadSearchChatContextAndDeleteFlow() throws Exception {
        String accessToken = signupAndGetAccessToken();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "loop-notes.txt",
                MediaType.TEXT_PLAIN_VALUE,
                """
                        A loop index changes every iteration.
                        If an array access is off by one, compare the expected index and the actual index first.
                        Rubber duck debugging works best when the learner explains the smallest failing example.
                        """.getBytes(StandardCharsets.UTF_8)
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("loop notes"))
                .andExpect(jsonPath("$.data.indexingStatus").value("indexed"))
                .andReturn();

        String documentId = readData(uploadResult).get("id").asText();

        mockMvc.perform(get("/api/documents")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(documentId))
                .andExpect(jsonPath("$.data[0].ragEnabled").value(true));

        mockMvc.perform(post("/api/documents/search")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "query", "loop index array access",
                                "limit", 3
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].documentId").value(documentId))
                .andExpect(jsonPath("$.data.results[0].content").value(containsString("loop index")));

        String conversationId = startConversation(accessToken);

        mockMvc.perform(post("/api/chat/messages")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "conversationId", Long.parseLong(conversationId),
                                "message", "How should I debug a loop index bug?",
                                "inputType", "text"
                        ))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(delete("/api/documents/" + documentId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/documents")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private String startConversation(String accessToken) throws Exception {
        MvcResult startResult = mockMvc.perform(post("/api/chat/conversations")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Loop debugging",
                                "topic", "Loops"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return readData(startResult).get("id").asText();
    }

    private String signupAndGetAccessToken() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        MvcResult signupResult = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Document Tester",
                                "email", "document-" + suffix + "@example.com",
                                "login_id", "document" + suffix,
                                "password", "Ducky123!"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        return readData(signupResult).get("accessToken").asText();
    }

    private JsonNode readData(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data");
    }
}
