package com.rubberduck.domain.document.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

class AwsDocumentAdapterTest {
  @Test
  void requiresExplicitCredentialsAndConstructsLocalEmulatorWithoutNetwork() {
    var settings =
        new CloudDocumentSettings(
            "cloud",
            "bucket",
            "ap-southeast-2",
            "http://localhost/queue",
            "release",
            "ducky-learning-v2",
            "ducky-page-chunks-v2");
    var completion = mock(DocumentCompletionService.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new AwsDocumentAdapter(settings, completion, "", "", "", false))
        .hasMessage("EXPLICIT_DOCUMENT_CREDENTIALS_REQUIRED");
    var adapter =
        new AwsDocumentAdapter(
            settings, completion, "emulator", "", "http://127.0.0.1:4566", false);
    adapter.poll();
    adapter.close();
    verifyNoInteractions(completion);
  }

  @Test
  void onlyAcknowledgesAfterSuccessfulCompletionReturn() {
    var s3 = mock(S3Client.class);
    var sqs = mock(SqsClient.class);
    var completion = mock(DocumentCompletionService.class);
    var settings =
        new CloudDocumentSettings(
            "cloud",
            "bucket",
            "ap-southeast-2",
            "http://localhost/queue",
            "release",
            "ducky-learning-v2",
            "ducky-page-chunks-v2");
    var adapter = new AwsDocumentAdapter(s3, sqs, settings, completion);
    when(sqs.receiveMessage(any(Consumer.class)))
        .thenReturn(
            ReceiveMessageResponse.builder()
                .messages(Message.builder().body("fixture").receiptHandle("receipt").build())
                .build());
    doThrow(new IllegalStateException("DB_UNAVAILABLE")).when(completion).complete("fixture");
    adapter.poll();
    verify(sqs, never()).deleteMessage(any(Consumer.class));
    doNothing().when(completion).complete("fixture");
    adapter.poll();
    var order = inOrder(completion, sqs);
    order.verify(completion, times(2)).complete("fixture");
    order.verify(sqs).deleteMessage(any(Consumer.class));
  }
}
