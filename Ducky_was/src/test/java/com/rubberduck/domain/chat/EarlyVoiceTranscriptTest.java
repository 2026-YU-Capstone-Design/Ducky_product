package com.rubberduck.domain.chat;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.rubberduck.domain.auth.service.AuthTokenService;
import com.rubberduck.domain.chat.service.ChatResponseService;
import com.rubberduck.domain.chat.service.ChatResponseService.AiReply;
import com.rubberduck.domain.chat.service.ChatService;
import com.rubberduck.support.ApiFlowTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:early_voice;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
class EarlyVoiceTranscriptTest extends ApiFlowTest {
    @Autowired ChatService chat;
    @Autowired AuthTokenService tokens;
    @MockitoBean ChatResponseService responses;

    @Test
    void historyShowsTranscriptWhilePiWaitsForReplyAndThenShowsEachMessageOnce() throws Exception {
        var user = newUser("early-voice");
        var pi = provision("early-voice-" + user.getId(), user);
        String conversationId = chat.startConversation(user, pi.device().getId(), null, null, null).id();
        String transcript = "파이썬 리스트의 인덱스 오류를 확인하고 싶어";
        String answer = "먼저 리스트의 길이를 확인해 보세요.";
        String body = new ObjectMapper().writeValueAsString(Map.of(
                "deviceId", pi.device().getSerialNumber(), "conversationId", Long.parseLong(conversationId),
                "message", transcript, "inputType", "voice"));
        String ownerAuth = "Bearer " + tokens.issue(user.getId());
        CountDownLatch generating = new CountDownLatch(1);
        CountDownLatch releaseReply = new CountDownLatch(1);
        when(responses.generateRoutedTurn(anyString(), any(), anyString(), anyList(), any(), any(), anyList()))
                .thenAnswer(call -> {
                    generating.countDown();
                    assertTrue(releaseReply.await(10, TimeUnit.SECONDS));
                    return new AiReply(answer, "answer", "rule");
                });

        var executor = Executors.newSingleThreadExecutor();
        try {
            var piRequest = executor.submit(() -> mockMvc.perform(post("/api/duck/conversation")
                    .header("Authorization", pi.authorization()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.message").value(answer)).andReturn());
            assertTrue(generating.await(10, TimeUnit.SECONDS));

            mockMvc.perform(get("/api/chat/conversations/" + conversationId).header("Authorization", ownerAuth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.messageCount").value(1))
                    .andExpect(jsonPath("$.data.messages.length()").value(1))
                    .andExpect(jsonPath("$.data.messages[0].role").value("user"))
                    .andExpect(jsonPath("$.data.messages[0].content").value(transcript));

            releaseReply.countDown();
            piRequest.get(10, TimeUnit.SECONDS);
            mockMvc.perform(get("/api/chat/conversations/" + conversationId).header("Authorization", ownerAuth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.messageCount").value(2))
                    .andExpect(jsonPath("$.data.messages.length()").value(2))
                    .andExpect(jsonPath("$.data.messages[0].content").value(transcript))
                    .andExpect(jsonPath("$.data.messages[1].content").value(answer));
        } finally {
            releaseReply.countDown();
            executor.shutdownNow();
        }
    }
}
