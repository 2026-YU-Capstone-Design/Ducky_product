package com.rubberduck.domain.document.service;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

@Component
@ConditionalOnProperty(name = "document.mode", havingValue = "cloud")
public class AwsDocumentAdapter implements DocumentCloudStore {
  private static final Logger log = LoggerFactory.getLogger(AwsDocumentAdapter.class);
  private final S3Client s3;
  private final SqsClient sqs;
  private final CloudDocumentSettings settings;
  private final DocumentCompletionService completion;
  private final boolean pollEnabled;

  @org.springframework.beans.factory.annotation.Autowired
  public AwsDocumentAdapter(
      CloudDocumentSettings settings,
      DocumentCompletionService completion,
      @Value("${document.cloud.credentials:}") String credentials,
      @Value("${document.cloud.profile:}") String profile,
      @Value("${document.cloud.endpoint:}") String endpoint,
      @Value("${document.cloud.poll-enabled:true}") boolean pollEnabled) {
    this.settings = settings;
    this.completion = completion;
    this.pollEnabled = pollEnabled;
    // Never silently use the developer's default account. Deployment must explicitly choose
    // instance role or named profile.
    software.amazon.awssdk.auth.credentials.AwsCredentialsProvider provider;
    if (credentials.equals("instance-role") && endpoint.isBlank())
      provider = InstanceProfileCredentialsProvider.create();
    else if (credentials.equals("profile") && !profile.isBlank() && endpoint.isBlank())
      provider = ProfileCredentialsProvider.create(profile);
    else if (credentials.equals("emulator") && !endpoint.isBlank()) {
      URI uri = URI.create(endpoint);
      if (!java.util.List.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost()))
        throw new IllegalArgumentException("LOCAL_EMULATOR_REQUIRED");
      provider = StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
    } else throw new IllegalArgumentException("EXPLICIT_DOCUMENT_CREDENTIALS_REQUIRED");
    var config =
        ClientOverrideConfiguration.builder()
            .apiCallTimeout(Duration.ofSeconds(90))
            .apiCallAttemptTimeout(Duration.ofSeconds(40))
            .build();
    var sb =
        S3Client.builder()
            .region(Region.of(settings.region()))
            .credentialsProvider(provider)
            .overrideConfiguration(config);
    var qb =
        SqsClient.builder()
            .region(Region.of(settings.region()))
            .credentialsProvider(provider)
            .overrideConfiguration(config);
    if (!endpoint.isBlank()) {
      sb.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
      qb.endpointOverride(URI.create(endpoint));
    }
    s3 = sb.build();
    sqs = qb.build();
  }

  AwsDocumentAdapter(
      S3Client s3,
      SqsClient sqs,
      CloudDocumentSettings settings,
      DocumentCompletionService completion) {
    this.s3 = s3;
    this.sqs = sqs;
    this.settings = settings;
    this.completion = completion;
    this.pollEnabled = true;
  }

  @Override
  public Uploaded upload(String key, byte[] data, String digest) {
    var response =
        s3.putObject(
            r ->
                r.bucket(settings.bucket())
                    .key(key)
                    .ifNoneMatch("*")
                    .metadata(Map.of("source-sha256", digest)),
            RequestBody.fromBytes(data));
    return new Uploaded(
        response.versionId(), response.eTag() == null ? null : response.eTag().replace("\"", ""));
  }

  @Override
  public byte[] read(String key, String version, int cap) {
    var request =
        software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
            .bucket(settings.bucket())
            .key(key);
    if (version != null) request.versionId(version);
    try (var stream = s3.getObject(request.build())) {
      if (stream.response().contentLength() > cap)
        throw new IllegalArgumentException("OBJECT_SIZE_LIMIT");
      byte[] bytes = stream.readNBytes(cap + 1);
      if (bytes.length > cap) throw new IllegalArgumentException("OBJECT_SIZE_LIMIT");
      return bytes;
    } catch (java.io.IOException e) {
      throw new IllegalStateException("OBJECT_READ_FAILED", e);
    }
  }

  @Scheduled(fixedDelayString = "${document.cloud.poll-interval-ms:2000}")
  public void poll() {
    if (!pollEnabled) return;
    try {
      var messages =
          sqs.receiveMessage(
                  r ->
                      r.queueUrl(settings.queueUrl())
                          .maxNumberOfMessages(1)
                          .waitTimeSeconds(10)
                          .visibilityTimeout(900))
              .messages();
      for (var message : messages) {
        try {
          completion.complete(message.body());
          sqs.deleteMessage(
              r -> r.queueUrl(settings.queueUrl()).receiptHandle(message.receiptHandle()));
        } catch (RuntimeException e) {
          log.warn("Document completion retained for retry ({})", e.getClass().getSimpleName());
        }
      }
    } catch (RuntimeException e) {
      log.warn("Document queue unavailable ({})", e.getClass().getSimpleName());
    }
  }

  @PreDestroy
  public void close() {
    s3.close();
    sqs.close();
  }
}
