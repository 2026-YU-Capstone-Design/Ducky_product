package com.rubberduck.domain.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rubberduck.domain.chat.entity.ChatMessage;
import com.rubberduck.domain.chat.entity.Conversation;
import com.rubberduck.domain.chat.repository.ChatMessageRepository;
import com.rubberduck.domain.chat.repository.ConversationRepository;
import com.rubberduck.domain.document.entity.DocumentChunk;
import com.rubberduck.domain.document.entity.LearningDocument;
import com.rubberduck.domain.document.repository.DocumentChunkRepository;
import com.rubberduck.domain.document.repository.LearningDocumentRepository;
import com.rubberduck.domain.user.entity.User;
import com.rubberduck.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.hibernate.tool.schema.spi.SchemaManagementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:manual-release-upgrade;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "document.pipeline-enabled=false",
    "chat.response-mode=extractive"
})
@ActiveProfiles("test")
@DirtiesContext
class ManualReleaseSchemaTest {
    @Autowired DataSource dataSource;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired UserRepository users;
    @Autowired ConversationRepository conversations;
    @Autowired ChatMessageRepository messages;
    @Autowired LearningDocumentRepository documents;
    @Autowired DocumentChunkRepository chunks;

    @DynamicPropertySource
    static void isolatedPostgres(DynamicPropertyRegistry registry) {
        String url = System.getenv("RELEASE_SCHEMA_TEST_DB_URL");
        if (url == null) return;
        // This test creates/drops tables: reject shared/remote database URLs.
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/ducky_release_schema_test")) {
            throw new IllegalArgumentException("DISPOSABLE_LOOPBACK_SCHEMA_DATABASE_REQUIRED");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", () -> "ducky_schema_test");
        registry.add("spring.datasource.password", () -> "local-schema-test-only");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RELEASE_SCHEMA_TEST_DB_URL", matches = ".+")
    void upgradesTheProductionLegacyDocumentAndChatSchema() throws Exception {
        User owner = users.saveAndFlush(User.create(
                "Upgrade fixture", "upgrade@example.com", "upgrade-owner", "unused-test-password"));
        LearningDocument document = documents.saveAndFlush(LearningDocument.create(
                owner, "Legacy notes", "legacy.pdf", "application/pdf", 1024, "pdf"));
        DocumentChunk chunk = chunks.saveAndFlush(DocumentChunk.create(document, 0, "Legacy excerpt", "[]"));
        Conversation conversation = conversations.saveAndFlush(
                Conversation.start(owner, null, null, "Legacy chat", "Schema upgrade"));
        ChatMessage message = messages.saveAndFlush(ChatMessage.create(
                conversation, "assistant", "Preserved answer", "answer", "text", 1));

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE document_upload_attempts");
        jdbc.execute("""
                ALTER TABLE learning_documents
                    DROP COLUMN source_key, DROP COLUMN source_type, DROP COLUMN source_sha256,
                    DROP COLUMN processing_token, DROP COLUMN attempt_id, DROP COLUMN source_bucket,
                    DROP COLUMN source_version_id, DROP COLUMN source_etag, DROP COLUMN completed_job_id,
                    DROP COLUMN processor_revision, DROP COLUMN processing_started_at,
                    DROP COLUMN processing_seconds, DROP COLUMN chunk_count,
                    DROP COLUMN processing_error, DROP COLUMN processing_warnings
                """);
        jdbc.execute("""
                ALTER TABLE document_chunks
                    DROP COLUMN embedding_model, DROP COLUMN block_id,
                    DROP COLUMN heading_path, DROP COLUMN provenance_json
                """);
        jdbc.execute("ALTER TABLE conversations DROP COLUMN selected_document_ids_json");
        jdbc.execute("""
                ALTER TABLE messages
                    DROP COLUMN sources_json, DROP COLUMN response_mode, DROP COLUMN response_strategy
                """);

        var schema = entityManagerFactory.unwrap(SessionFactory.class).getSchemaManager();
        try (Connection connection = dataSource.getConnection()) {
            migrate(connection, "20261119_document_cloud.sql");
            migrate(connection, "20261120_conversation_harness.sql");
            assertThatThrownBy(schema::validateMappedObjects)
                    .isInstanceOf(SchemaManagementException.class)
                    .hasMessageContaining("document_chunks");
            migrate(connection, "20261124_document_legacy_schema_bridge.sql");
            schema.validateMappedObjects();

            migrate(connection, "20261119_document_cloud.sql");
            migrate(connection, "20261120_conversation_harness.sql");
            migrate(connection, "20261124_document_legacy_schema_bridge.sql");
            schema.validateMappedObjects();
        }

        assertThat(documents.findById(document.getId()).orElseThrow().getTitle()).isEqualTo("Legacy notes");
        assertThat(chunks.findById(chunk.getId()).orElseThrow().getContent()).isEqualTo("Legacy excerpt");
        ChatMessage migratedMessage = messages.findById(message.getId()).orElseThrow();
        assertThat(migratedMessage.getMessageText()).isEqualTo("Preserved answer");
        assertThat(migratedMessage.getSources()).isEmpty();
        assertThat(migratedMessage.getResponseMode()).isNull();
    }

    private static void migrate(Connection connection, String fileName) {
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/manual/" + fileName));
    }

    @Test
    void upgradesLegacyRowsAndCanBeAppliedTwiceBeforeHibernateValidation() throws Exception {
        User owner = users.saveAndFlush(User.create(
                "Schema owner", "schema@example.com", "schema-owner", "unused-test-password"));
        Conversation conversation = conversations.saveAndFlush(
                Conversation.start(owner, null, null, "Legacy conversation", "Schema upgrade"));
        ChatMessage message = messages.saveAndFlush(ChatMessage.create(
                conversation, "assistant", "Legacy answer", "answer", "text", 1));

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("ALTER TABLE conversations DROP COLUMN selected_document_ids_json");
        jdbc.execute("ALTER TABLE messages DROP COLUMN response_strategy");
        var schema = entityManagerFactory.unwrap(SessionFactory.class).getSchemaManager();
        assertThatThrownBy(schema::validateMappedObjects)
                .isInstanceOf(SchemaManagementException.class);

        for (int attempt = 0; attempt < 2; attempt++) {
            try (Connection connection = dataSource.getConnection()) {
                migrate(connection, "20261120_conversation_harness.sql");
            }
            schema.validateMappedObjects();
        }

        conversation = conversations.findById(conversation.getId()).orElseThrow();
        message = messages.findById(message.getId()).orElseThrow();
        assertThat(conversation.getTitle()).isEqualTo("Legacy conversation");
        assertThat(message.getMessageText()).isEqualTo("Legacy answer");
        assertThat(conversation.getSelectedDocumentIdsJson()).isNull();
        assertThat(message.getResponseStrategy()).isNull();
        conversation.setSelectedDocumentIdsJson("[123]");
        message.setResponseStrategy("DOCUMENT");
        conversations.saveAndFlush(conversation);
        messages.saveAndFlush(message);
        assertThat(conversations.findById(conversation.getId()).orElseThrow()
                .getSelectedDocumentIdsJson()).isEqualTo("[123]");
        assertThat(messages.findById(message.getId()).orElseThrow()
                .getResponseStrategy()).isEqualTo("DOCUMENT");
        try (Connection connection = dataSource.getConnection()) {
            System.out.println("Manual release schema validated on "
                    + connection.getMetaData().getDatabaseProductName() + " "
                    + connection.getMetaData().getDatabaseProductVersion());
        }
    }
}
