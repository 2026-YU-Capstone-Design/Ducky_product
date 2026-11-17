package com.rubberduck.domain.iot;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rubberduck.support.ApiFlowTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class IotCompatibilityFlowTest extends ApiFlowTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void duckConversationRequiresConfiguredAiResponseService() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String deviceId = "raspberry-duck-" + suffix;
        String userId = "iot-user-" + suffix;
        TestDevice pi = provision(deviceId, newUser("iot"));

        mockMvc.perform(post("/api/duck/conversation")
                        .header("Authorization", pi.authorization())
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
        TestDevice pi = provision(deviceId, newUser("iot"));

        mockMvc.perform(post("/api/iot/state")
                        .header("Authorization", pi.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "device_id", deviceId,
                                "current_state", "LISTENING"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.result").value(true));

        mockMvc.perform(post("/api/iot/stt-result")
                        .header("Authorization", pi.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "device_id", deviceId,
                                "user_id", userId,
                                "stt_text", "Java exception handling is confusing"
                        ))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/iot/tts-complete")
                        .header("Authorization", pi.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "device_id", deviceId
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value(true));

        mockMvc.perform(post("/api/iot/error")
                        .header("Authorization", pi.authorization())
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
