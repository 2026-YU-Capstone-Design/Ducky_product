package com.rubberduck.domain.iot;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
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
    void duckConversationSyncStoresTurnWithoutAi() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String deviceId = "raspberry-duck-sync-" + suffix;
        String userId = "iot-sync-user-" + suffix;
        String clientTurnId = "turn-" + suffix;

        mockMvc.perform(post("/api/duck/conversation/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "deviceId", deviceId,
                                "userId", userId,
                                "clientTurnId", clientTurnId,
                                "userMessage", "오프라인에서 물어본 질문",
                                "assistantMessage", "오프라인에서 받은 답변",
                                "inputType", "voice",
                                "sttSuccess", true,
                                "ttsSuccess", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.synced").value(true))
                .andExpect(jsonPath("$.data.conversationId").isNumber());

        mockMvc.perform(post("/api/duck/conversation/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "deviceId", deviceId,
                                "userId", userId,
                                "clientTurnId", clientTurnId,
                                "userMessage", "오프라인에서 물어본 질문",
                                "assistantMessage", "오프라인에서 받은 답변",
                                "inputType", "voice",
                                "sttSuccess", true,
                                "ttsSuccess", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.synced").value(true));
    }

    @Test
    void duckDeviceProfileReturnsDefaultsWhenUnlinked() throws Exception {
        String deviceId = "raspberry-duck-profile-" + System.nanoTime();

        mockMvc.perform(get("/api/duck/device-profile")
                        .param("deviceId", deviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processing").value("active"))
                .andExpect(jsonPath("$.data.expression").value("visual"))
                .andExpect(jsonPath("$.data.understanding").value("sequential"));
    }

    @Test
    void duckConversationRequiresConfiguredAiResponseService() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String deviceId = "raspberry-duck-" + suffix;
        String userId = "iot-user-" + suffix;

        mockMvc.perform(post("/api/duck/conversation")
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
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void iotStateTtsAndErrorEndpointsWorkWithoutAiResponse() throws Exception {
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

        mockMvc.perform(post("/api/iot/stt-result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "device_id", deviceId,
                                "user_id", userId,
                                "stt_text", "Java exception handling is confusing"
                        ))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/iot/tts-complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "device_id", deviceId,
                                "message_id", 1
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
