package com.rubberduck.domain.iot;

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
class IotCompatibilityFlowTest {

    @Autowired
    private WebApplicationContext context;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void duckConversationAndLogFlowWorksForCurrentRaspberryClient() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String deviceId = "raspberry-duck-" + suffix;
        String userId = "iot-user-" + suffix;

        MvcResult conversationResult = mockMvc.perform(post("/api/duck/conversation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "deviceId", deviceId,
                                "userId", userId,
                                "inputType", "voice",
                                "message", "Python loop index error",
                                "learningType", Map.of(
                                        "processing", "reflective",
                                        "expression", "verbal",
                                        "structure", "sequential"
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").isNumber())
                .andExpect(jsonPath("$.responseType").value("question"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.shouldSaveLog").value(true))
                .andReturn();

        JsonNode conversationBody = objectMapper.readTree(conversationResult.getResponse().getContentAsString());
        long conversationId = conversationBody.get("conversationId").asLong();
        String assistantMessage = conversationBody.get("message").asText();

        mockMvc.perform(post("/api/duck/conversation/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "conversationId", conversationId,
                                "deviceId", deviceId,
                                "userId", userId,
                                "userMessage", "Python loop index error",
                                "assistantMessage", assistantMessage,
                                "sttSuccess", true,
                                "ttsSuccess", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
    }

    @Test
    void iotStateSttTtsAndErrorEndpointsWork() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String deviceId = "raspberry-duck-iot-" + suffix;
        String userId = "iot-state-user-" + suffix;

        mockMvc.perform(post("/api/iot/state")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "device_id", deviceId,
                                "current_state", "LISTENING"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.result").value(true));

        MvcResult sttResult = mockMvc.perform(post("/api/iot/stt-result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "device_id", deviceId,
                                "user_id", userId,
                                "stt_text", "Java exception handling is confusing"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.aiResponse.conversationId").isString())
                .andExpect(jsonPath("$.data.aiResponse.aiResponse.role").value("assistant"))
                .andReturn();

        JsonNode data = objectMapper.readTree(sttResult.getResponse().getContentAsString()).get("data");
        long assistantMessageId = data.get("aiResponse").get("aiResponse").get("id").asLong();

        mockMvc.perform(post("/api/iot/tts-complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "device_id", deviceId,
                                "message_id", assistantMessageId
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value(true));

        mockMvc.perform(post("/api/iot/error")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "device_id", deviceId,
                                "errorCode", "AUDIO_PLAYBACK_FAILED",
                                "errorMessage", "aplay command failed"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value(true));
    }
}
