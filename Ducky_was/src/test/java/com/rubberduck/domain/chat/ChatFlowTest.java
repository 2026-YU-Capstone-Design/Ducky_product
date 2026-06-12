package com.rubberduck.domain.chat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class ChatFlowTest {

    @Autowired
    private WebApplicationContext context;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void chatConversationMessageHintAndEndFlow() throws Exception {
        String accessToken = signupAndGetAccessToken();

        MvcResult startResult = mockMvc.perform(post("/api/chat/conversations")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Next.js 라우팅 설명",
                                "topic", "App Router"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("in_progress"))
                .andExpect(jsonPath("$.data.messageCount").value(0))
                .andReturn();

        String conversationId = readData(startResult).get("id").asText();

        mockMvc.perform(post("/api/chat/messages")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "conversation_id", Long.parseLong(conversationId),
                                "message_text", "layout.tsx와 page.tsx 차이를 잘 모르겠어",
                                "input_type", "text"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").value(conversationId))
                .andExpect(jsonPath("$.data.userMessage.role").value("user"))
                .andExpect(jsonPath("$.data.aiResponse.role").value("assistant"))
                .andExpect(jsonPath("$.data.aiResponse.type").value("question"));

        mockMvc.perform(post("/api/chat/hints")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "conversation_id", Long.parseLong(conversationId)
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hintMessage.type").value("hint"))
                .andExpect(jsonPath("$.data.hintMessage.hintLevel").value(1))
                .andExpect(jsonPath("$.data.hintMessage.hintNumber").value(1));

        mockMvc.perform(get("/api/chat/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messageCount").value(3))
                .andExpect(jsonPath("$.data.hintCount").value(1))
                .andExpect(jsonPath("$.data.messages[0].role").value("user"))
                .andExpect(jsonPath("$.data.messages[1].role").value("assistant"))
                .andExpect(jsonPath("$.data.messages[2].type").value("hint"));

        mockMvc.perform(patch("/api/chat/conversations/" + conversationId + "/end")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("completed"))
                .andExpect(jsonPath("$.data.feedbackMessage.type").value("feedback"));

        mockMvc.perform(get("/api/chat/conversations")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(conversationId));
    }

    private String signupAndGetAccessToken() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        MvcResult signupResult = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Chat Tester",
                                "email", "chat-" + suffix + "@example.com",
                                "login_id", "chat" + suffix,
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
