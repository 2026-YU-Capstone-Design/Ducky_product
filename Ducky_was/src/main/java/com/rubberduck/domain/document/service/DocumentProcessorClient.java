package com.rubberduck.domain.document.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class DocumentProcessorClient {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String url;
    private final String token;
    private final long timeout;

    public DocumentProcessorClient(@Value("${document.worker-url:http://127.0.0.1:8091}") String url,
            @Value("${document.worker-token:}") String token,
            @Value("${document.processing-timeout-seconds:300}") long timeout) {
        this.url = url;
        this.token = token;
        this.timeout = timeout;
    }

    public JsonNode process(Long documentId, String sourceType, byte[] bytes) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url + "/process?documentId=" + documentId + "&sourceType=" + sourceType))
                .timeout(Duration.ofSeconds(timeout))
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes));
        if (!token.isBlank()) request.header("Authorization", "Bearer " + token);
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode result = mapper.readTree(response.body());
        if (response.statusCode() != 200) {
            String code = result.path("error").asText("PROCESSING_FAILED");
            if (!code.matches("[A-Z_]{1,60}")) code = "PROCESSING_FAILED";
            throw new IllegalStateException(code);
        }
        return result;
    }
}
