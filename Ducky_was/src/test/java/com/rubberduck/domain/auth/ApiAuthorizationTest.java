package com.rubberduck.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rubberduck.domain.auth.service.AuthTokenService;
import com.rubberduck.domain.chat.entity.ChatMessage;
import com.rubberduck.domain.chat.entity.Conversation;
import com.rubberduck.domain.chat.repository.ChatMessageRepository;
import com.rubberduck.domain.chat.repository.ConversationRepository;
import com.rubberduck.domain.device.entity.Device;
import com.rubberduck.domain.device.entity.DeviceCommand;
import com.rubberduck.domain.device.entity.DeviceUser;
import com.rubberduck.domain.device.repository.DeviceCommandRepository;
import com.rubberduck.domain.iot.repository.IotEventRepository;
import com.rubberduck.domain.user.entity.User;
import com.rubberduck.support.ApiFlowTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@ActiveProfiles("test")
class ApiAuthorizationTest extends ApiFlowTest {

    @Autowired private AuthTokenService tokens;
    @Autowired private ConversationRepository conversations;
    @Autowired private ChatMessageRepository messages;
    @Autowired private DeviceCommandRepository commands;
    @Autowired private IotEventRepository events;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void protectedRoutesRequireCredentialsBeforeReadingTheBody() throws Exception {
        for (String route : List.of("/api/iot/state", "/api/iot/stt-result", "/api/iot/tts-complete",
                "/api/iot/error", "/api/iot/commands/next", "/api/iot/commands/1/complete",
                "/api/duck/conversation", "/api/duck/conversation/logs", "/api/devices",
                "/api/devices/1/link", "/api/chat/messages", "/api/documents/search")) {
            mockMvc.perform(post(route)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
        }
        mockMvc.perform(get("/api/duck/active-conversation")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void userAndDeviceCredentialsHaveSeparatePermissions() throws Exception {
        User user = newUser("roles");
        String bearer = "Bearer " + tokens.issue(user.getId());
        TestDevice pi = provision("roles-" + user.getId(), user);
        String expired = new AuthTokenService("test-token-secret", -5).issue(user.getId());
        for (String invalid : List.of("Bearer invalid", "Bearer " + expired, bearer + "x", bearer + ".")) {
            mockMvc.perform(get("/api/users/me").header("Authorization", invalid)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/iot/state").header("Authorization", bearer)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users/me").header("Authorization", pi.authorization())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/iot/state").header("Authorization", "Bearer device." + "x".repeat(43)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/duck/active-conversation").param("deviceId", "wrong")
                .header("Authorization", pi.authorization())).andExpect(status().isForbidden());
    }

    @Test
    void serialAndHistoricLinksCannotClaimDevices() throws Exception {
        User owner = newUser("owner");
        User other = newUser("other");
        TestDevice pi = provision("private-" + owner.getId(), owner);
        Device device = pi.device();
        links.save(DeviceUser.link(device, other, "OWNER"));
        String otherToken = "Bearer " + tokens.issue(other.getId());
        mockMvc.perform(get("/api/devices").header("Authorization", otherToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mockMvc.perform(body("/api/devices", otherToken, Map.of("serial_number", device.getSerialNumber())))
                .andExpect(status().isForbidden());
        mockMvc.perform(body("/api/devices/" + device.getId() + "/link", otherToken, Map.of("user_id", other.getId().toString())))
                .andExpect(status().isForbidden());
        mockMvc.perform(body("/api/devices/" + device.getId() + "/link", "Bearer " + tokens.issue(owner.getId()),
                Map.of("user_id", other.getId().toString(), "role", "OWNER"))).andExpect(status().isForbidden());
        mockMvc.perform(body("/api/chat/conversations", otherToken, Map.of("deviceId", device.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(body("/api/devices/" + device.getId() + "/commands/start-recording", otherToken, Map.of()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deviceMayOnlyAccessConversationsAndCommandsForItsOwnPair() throws Exception {
        User owner = newUser("chat");
        User other = newUser("victim");
        TestDevice pi = provision("pi-" + owner.getId(), owner);
        TestDevice sibling = provision("second-" + owner.getId(), owner);
        String serial = pi.device().getSerialNumber();
        Conversation victim = conversations.save(Conversation.start(other, null, null, null, null));
        Conversation siblingChat = conversations.save(Conversation.start(owner, sibling.device(), null, null, null));
        long baseline = events.count();
        for (Conversation forbidden : List.of(victim, siblingChat)) {
            Map<String, Object> payload = Map.of("deviceId", serial, "userId", other.getId().toString(),
                    "conversationId", forbidden.getId(), "message", "hello");
            mockMvc.perform(body("/api/duck/conversation", pi.authorization(), payload)).andExpect(status().isForbidden());
            mockMvc.perform(body("/api/duck/conversation/logs", pi.authorization(), payload)).andExpect(status().isForbidden());
            mockMvc.perform(body("/api/iot/stt-result", pi.authorization(), Map.of("device_id", serial,
                    "conversation_id", forbidden.getId(), "stt_text", "hello"))).andExpect(status().isForbidden());
        }
        ChatMessage message = messages.save(ChatMessage.create(siblingChat, "assistant", "private", "answer", "system", 1));
        mockMvc.perform(body("/api/iot/tts-complete", pi.authorization(),
                Map.of("device_id", serial, "message_id", message.getId()))).andExpect(status().isForbidden());
        mockMvc.perform(body("/api/iot/state", pi.authorization(), Map.of("device_id", sibling.device().getSerialNumber(),
                "current_state", "ERROR"))).andExpect(status().isForbidden());
        assertThat(events.count()).isEqualTo(baseline);
        mockMvc.perform(get("/api/duck/active-conversation").header("Authorization", pi.authorization())
                .param("deviceId", serial).param("userId", other.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conversationId").isEmpty());

        DeviceCommand foreign = commands.save(DeviceCommand.startRecording(sibling.device(), owner, siblingChat.getId()));
        mockMvc.perform(body("/api/iot/commands/" + foreign.getId() + "/complete", pi.authorization(),
                Map.of("device_id", serial, "success", true))).andExpect(status().isForbidden());
        mockMvc.perform(body("/api/iot/commands/next", pi.authorization(), Map.of("device_id", serial)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.available").value(false));
        mockMvc.perform(get("/api/chat/conversations/" + victim.getId()).header("Authorization", "Bearer " + tokens.issue(owner.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorizedVoiceTurnAndLogUseTheOwnerFromTheDeviceToken() throws Exception {
        User owner = newUser("voice");
        User other = newUser("body");
        TestDevice pi = provision("voice-" + owner.getId(), owner);
        String serial = pi.device().getSerialNumber();
        var result = mockMvc.perform(body("/api/duck/conversation", pi.authorization(), Map.of(
                        "deviceId", serial, "userId", other.getId().toString(), "message", "x")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").isString()).andReturn();
        long id = json.readTree(result.getResponse().getContentAsByteArray()).get("conversationId").asLong();
        mockMvc.perform(get("/api/chat/conversations/" + id).header("Authorization", "Bearer " + tokens.issue(owner.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("success"));
        mockMvc.perform(get("/api/chat/conversations/" + id).header("Authorization", "Bearer " + tokens.issue(other.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(body("/api/duck/conversation/logs", pi.authorization(), Map.of(
                        "deviceId", serial, "userId", other.getId().toString(), "conversationId", id,
                        "userMessage", "x", "sttSuccess", true, "ttsSuccess", true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.saved").value(true));

        var link = links.findByDeviceAndUserAndTokenHashIsNotNull(pi.device(), owner).orElseThrow();
        link.setTokenHash(null);
        links.save(link);
        mockMvc.perform(get("/api/duck/active-conversation").header("Authorization", pi.authorization()).param("deviceId", serial))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder body(String path, String token, Map<String, ?> payload) throws Exception {
        return post(path).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(payload));
    }
}
