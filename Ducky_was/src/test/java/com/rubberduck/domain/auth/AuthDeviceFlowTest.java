package com.rubberduck.domain.auth;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rubberduck.domain.auth.service.KakaoOAuthClient;
import com.rubberduck.domain.device.entity.Device;
import com.rubberduck.domain.device.service.DeviceService;
import com.rubberduck.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class AuthDeviceFlowTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private DeviceService deviceService;

    @MockitoBean
    private KakaoOAuthClient kakaoOAuthClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void signupLoginMeAndDeviceLinkFlow() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String email = "ducky-" + suffix + "@example.com";
        String loginId = "ducky" + suffix;
        String password = "Ducky123!";

        MvcResult signupResult = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Ducky Tester",
                                "email", email,
                                "login_id", loginId,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.userInfo.email").value(email))
                .andReturn();

        JsonNode signupData = readData(signupResult);
        String userId = signupData.get("userInfo").get("id").asText();

        mockMvc.perform(get("/api/auth/email-available")
                        .param("email", email.toUpperCase(Locale.ROOT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));

        mockMvc.perform(get("/api/auth/email-available")
                        .param("email", "new-" + email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "login_id", loginId,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andReturn();

        String accessToken = readData(loginResult).get("accessToken").asText();

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userId))
                .andExpect(jsonPath("$.data.loginId").value(loginId));

        mockMvc.perform(patch("/api/users/me/learning-style")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "processing", "reflective",
                                "expression", "verbal",
                                "understanding", "global",
                                "onboarded", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.learningStyle.processing").value("reflective"))
                .andExpect(jsonPath("$.data.learningStyle.expression").value("verbal"))
                .andExpect(jsonPath("$.data.learningStyle.understanding").value("global"))
                .andExpect(jsonPath("$.data.onboarded").value(true));

        MvcResult deviceResult = mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "serial_number", "raspberry-duck-" + suffix,
                                "firmware_version", "0.1.0"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.status").value("REGISTERED"))
                .andReturn();

        String deviceId = readData(deviceResult).get("id").asText();

        mockMvc.perform(post("/api/devices/" + deviceId + "/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "user_id", userId,
                                "role", "OWNER"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceUserId").isString());

        mockMvc.perform(get("/api/devices")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(deviceId));

        MvcResult conversationResult = mockMvc.perform(post("/api/chat/conversations")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Raspberry voice session",
                                "topic", "Remote command"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        Long conversationId = readData(conversationResult).get("id").asLong();

        MvcResult secondConversationResult = mockMvc.perform(post("/api/chat/conversations")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Another session",
                                "topic", "Duplicate command"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        Long secondConversationId = readData(secondConversationResult).get("id").asLong();

        MvcResult commandResult = mockMvc.perform(post("/api/devices/" + deviceId + "/commands/start-recording")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "conversation_id", conversationId
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commandType").value("START_RECORDING"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.conversationId").value(conversationId))
                .andReturn();

        String commandId = readData(commandResult).get("id").asText();

        mockMvc.perform(post("/api/devices/" + deviceId + "/commands/start-recording")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "conversation_id", secondConversationId
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(commandId))
                .andExpect(jsonPath("$.data.conversationId").value(conversationId));

        mockMvc.perform(get("/api/devices/" + deviceId + "/commands/latest")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(commandId))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(post("/api/iot/commands/next")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "device_id", "raspberry-duck-" + suffix
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.commandId").value(Long.parseLong(commandId)))
                .andExpect(jsonPath("$.data.commandType").value("START_RECORDING"))
                .andExpect(jsonPath("$.data.conversationId").value(conversationId));

        mockMvc.perform(post("/api/iot/commands/next")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "device_id", "raspberry-duck-" + suffix
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));

        mockMvc.perform(post("/api/iot/commands/" + commandId + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "device_id", "raspberry-duck-" + suffix,
                                "success", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value(true));

        mockMvc.perform(get("/api/devices/" + deviceId + "/commands/latest")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void linkedDeviceResolvesMostRecentOwnerForIotConversationRouting() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        JsonNode firstSignup = signupUser(
                "first-" + suffix + "@example.com",
                "first" + suffix,
                "Ducky123!"
        );
        JsonNode secondSignup = signupUser(
                "second-" + suffix + "@example.com",
                "second" + suffix,
                "Ducky123!"
        );

        String firstUserId = firstSignup.get("userInfo").get("id").asText();
        String secondUserId = secondSignup.get("userInfo").get("id").asText();

        MvcResult deviceResult = mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "serial_number", "raspberry-duck-shared-" + suffix,
                                "firmware_version", "0.1.0"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        String deviceId = readData(deviceResult).get("id").asText();
        linkDevice(deviceId, firstUserId);
        linkDevice(deviceId, secondUserId);

        Device device = deviceService.getById(Long.parseLong(deviceId));
        assertThat(deviceService.findLinkedUser(device).map(User::getId))
                .contains(Long.parseLong(secondUserId));
    }

    @Test
    void kakaoLoginCreatesSocialUserAndReusesExistingAccount() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String code = "kakao-code-" + suffix;
        String redirectUri = "https://ducklab.site/auth/callback/kakao";
        String providerUserId = "98765" + suffix;

        when(kakaoOAuthClient.fetchUser(code, redirectUri))
                .thenReturn(new KakaoOAuthClient.KakaoUser(providerUserId, "", "Kakao Tester"));

        MvcResult firstLoginResult = mockMvc.perform(post("/api/auth/oauth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "redirectUri", redirectUri
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.userInfo.name").value("Kakao Tester"))
                .andExpect(jsonPath("$.data.userInfo.email").value(
                        "kakao_" + providerUserId + "@social.ducky.local"
                ))
                .andExpect(jsonPath("$.data.userInfo.loginId").value("kakao_" + providerUserId))
                .andReturn();

        String userId = readData(firstLoginResult).get("userInfo").get("id").asText();

        mockMvc.perform(post("/api/auth/oauth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "redirectUri", redirectUri
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userInfo.id").value(userId));
    }

    private JsonNode signupUser(String email, String loginId, String password) throws Exception {
        MvcResult signupResult = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Ducky Tester",
                                "email", email,
                                "login_id", loginId,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        return readData(signupResult);
    }

    private void linkDevice(String deviceId, String userId) throws Exception {
        mockMvc.perform(post("/api/devices/" + deviceId + "/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "user_id", userId,
                                "role", "OWNER"
                        ))))
                .andExpect(status().isOk());
    }

    private JsonNode readData(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data");
    }
}
