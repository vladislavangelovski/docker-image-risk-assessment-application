package com.finki.vladislavangelovski.ai_service.history;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finki.vladislavangelovski.common.dto.Citation;
import com.finki.vladislavangelovski.common.dto.QaConversationHistoryItem;
import com.finki.vladislavangelovski.common.dto.QaConversationMessage;
import com.finki.vladislavangelovski.common.dto.QaQuestionRequest;
import com.finki.vladislavangelovski.common.dto.QaQuestionResponse;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class JdbcQaConversationRepository implements QaConversationRepository {
  private static final Logger log = LoggerFactory.getLogger(JdbcQaConversationRepository.class);

  private static final int CHAT_SCOPE_MAX_LEN = 512;
  private static final int TITLE_MAX_LEN = 160;
  private static final String DEFAULT_SCOPE = "assessment|default";
  private static final TypeReference<List<Citation>> CITATION_LIST_TYPE = new TypeReference<>() {};

  private static final String INSERT_CONVERSATION_SQL =
      """
          INSERT INTO qa_chat_conversations (
              conversation_id,
              user_id,
              user_name,
              chat_scope_id,
              image_ref,
              title,
              created_at,
              updated_at
          )
          VALUES (?, ?, ?, ?, ?, ?, now(), now())
          """;

  private static final String INSERT_MESSAGE_SQL =
      """
          INSERT INTO qa_chat_messages (
              conversation_id,
              role,
              content,
              citations_json,
              created_at
          )
          VALUES (?, ?, ?, ?, now())
          """;

  private static final String UPDATE_CONVERSATION_SQL =
      """
          UPDATE qa_chat_conversations
             SET user_name = COALESCE(?, user_name),
                 image_ref = COALESCE(?, image_ref),
                 title = CASE WHEN title IS NULL OR title = 'New chat' THEN ? ELSE title END,
                 updated_at = now()
           WHERE conversation_id = ?
             AND user_id = ?
          """;

  private static final String SELECT_CONVERSATIONS_BY_SCOPE_SQL =
      """
          SELECT conversation_id, chat_scope_id, title, image_ref, created_at, updated_at
            FROM qa_chat_conversations
           WHERE user_id = ?
             AND chat_scope_id = ?
           ORDER BY updated_at DESC
           LIMIT ?
          """;

  private static final String SELECT_CONVERSATIONS_SQL =
      """
          SELECT conversation_id, chat_scope_id, title, image_ref, created_at, updated_at
            FROM qa_chat_conversations
           WHERE user_id = ?
           ORDER BY updated_at DESC
           LIMIT ?
          """;

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public JdbcQaConversationRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Override
  public String appendQuestionExchange(
      QaUserContext userContext, QaQuestionRequest request, QaQuestionResponse response) {
    if (userContext == null || !StringUtils.hasText(userContext.userId()) || request == null) {
      return null;
    }
    String prompt = normalizeText(request.question());
    if (!StringUtils.hasText(prompt)) {
      return null;
    }

    String chatScopeId = normalizeScope(request.chatScopeId(), request.imageRef());
    UUID conversationId =
        findExistingConversationId(userContext.userId(), request.conversationId(), chatScopeId);

    if (conversationId == null) {
      conversationId = UUID.randomUUID();
      jdbc.update(
          INSERT_CONVERSATION_SQL,
          conversationId,
          userContext.userId(),
          normalizeText(userContext.userName()),
          chatScopeId,
          normalizeText(request.imageRef()),
          buildConversationTitle(prompt));
    }

    appendMessage(conversationId, "user", prompt, null);

    String answer =
        response != null && StringUtils.hasText(response.answer())
            ? response.answer().trim()
            : "No answer was returned.";
    appendMessage(conversationId, "assistant", answer, serializeCitations(response));

    jdbc.update(
        UPDATE_CONVERSATION_SQL,
        normalizeText(userContext.userName()),
        normalizeText(request.imageRef()),
        buildConversationTitle(prompt),
        conversationId,
        userContext.userId());

    return conversationId.toString();
  }

  @Override
  public List<QaConversationHistoryItem> findRecentConversations(
      String userId, String chatScopeId, int limit) {
    if (!StringUtils.hasText(userId) || limit < 1) {
      return List.of();
    }

    String safeScope = normalizeScope(chatScopeId, null);
    boolean hasScope = StringUtils.hasText(chatScopeId);

    List<ConversationRow> conversations =
        hasScope
            ? jdbc.query(
                SELECT_CONVERSATIONS_BY_SCOPE_SQL,
                (rs, rowNum) ->
                    new ConversationRow(
                        rs.getObject("conversation_id", UUID.class),
                        rs.getString("chat_scope_id"),
                        rs.getString("title"),
                        rs.getString("image_ref"),
                        toInstant(rs.getObject("created_at", OffsetDateTime.class)),
                        toInstant(rs.getObject("updated_at", OffsetDateTime.class))),
                userId,
                safeScope,
                limit)
            : jdbc.query(
                SELECT_CONVERSATIONS_SQL,
                (rs, rowNum) ->
                    new ConversationRow(
                        rs.getObject("conversation_id", UUID.class),
                        rs.getString("chat_scope_id"),
                        rs.getString("title"),
                        rs.getString("image_ref"),
                        toInstant(rs.getObject("created_at", OffsetDateTime.class)),
                        toInstant(rs.getObject("updated_at", OffsetDateTime.class))),
                userId,
                limit);

    if (conversations.isEmpty()) {
      return List.of();
    }

    Map<UUID, List<QaConversationMessage>> messagesByConversation =
        findMessagesByConversationIds(
            conversations.stream().map(ConversationRow::conversationId).toList());

    List<QaConversationHistoryItem> items = new ArrayList<>(conversations.size());
    for (ConversationRow row : conversations) {
      items.add(
          new QaConversationHistoryItem(
              row.conversationId().toString(),
              row.chatScopeId(),
              row.title(),
              row.imageRef(),
              row.createdAt(),
              row.updatedAt(),
              messagesByConversation.getOrDefault(row.conversationId(), List.of())));
    }
    return items;
  }

  @Override
  public boolean deleteConversation(String userId, String conversationId) {
    if (!StringUtils.hasText(userId) || !StringUtils.hasText(conversationId)) {
      return false;
    }

    UUID parsedId = parseUuid(conversationId);
    if (parsedId == null) {
      return false;
    }

    int deleted =
        jdbc.update(
            "DELETE FROM qa_chat_conversations WHERE conversation_id = ? AND user_id = ?",
            parsedId,
            userId.trim());
    return deleted > 0;
  }

  private void appendMessage(
      UUID conversationId, String role, String content, String citationsJson) {
    jdbc.update(
        INSERT_MESSAGE_SQL,
        ps -> {
          ps.setObject(1, conversationId);
          ps.setString(2, role);
          ps.setString(3, content);
          if (citationsJson != null) {
            ps.setString(4, citationsJson);
          } else {
            ps.setNull(4, Types.VARCHAR);
          }
        });
  }

  private UUID findExistingConversationId(
      String userId, String conversationIdRaw, String chatScopeId) {
    UUID requested = parseUuid(conversationIdRaw);
    if (requested == null) {
      return null;
    }
    List<UUID> ids =
        jdbc.query(
            """
                SELECT conversation_id
                  FROM qa_chat_conversations
                 WHERE conversation_id = ?
                   AND user_id = ?
                   AND chat_scope_id = ?
                """,
            (rs, rowNum) -> rs.getObject("conversation_id", UUID.class),
            requested,
            userId.trim(),
            chatScopeId);
    if (ids.isEmpty()) {
      return null;
    }
    return ids.get(0);
  }

  private Map<UUID, List<QaConversationMessage>> findMessagesByConversationIds(
      List<UUID> conversationIds) {
    if (conversationIds == null || conversationIds.isEmpty()) {
      return Map.of();
    }

    String placeholders =
        conversationIds.stream().map(ignored -> "?").collect(Collectors.joining(","));
    String sql =
        """
            SELECT message_id, conversation_id, role, content, citations_json, created_at
              FROM qa_chat_messages
             WHERE conversation_id IN (%s)
             ORDER BY created_at ASC, message_id ASC
            """
            .formatted(placeholders);

    List<QaConversationMessageRow> rows =
        jdbc.query(
            sql,
            ps -> {
              for (int i = 0; i < conversationIds.size(); i++) {
                ps.setObject(i + 1, conversationIds.get(i));
              }
            },
            (rs, rowNum) ->
                new QaConversationMessageRow(
                    rs.getLong("message_id"),
                    rs.getObject("conversation_id", UUID.class),
                    rs.getString("role"),
                    rs.getString("content"),
                    rs.getString("citations_json"),
                    toInstant(rs.getObject("created_at", OffsetDateTime.class))));

    Map<UUID, List<QaConversationMessage>> mapped = new LinkedHashMap<>();
    for (QaConversationMessageRow row : rows) {
      mapped
          .computeIfAbsent(row.conversationId(), ignored -> new ArrayList<>())
          .add(
              new QaConversationMessage(
                  row.id(),
                  normalizeRole(row.role()),
                  row.content(),
                  parseCitations(row.citationsJson()),
                  row.createdAt()));
    }
    return mapped;
  }

  private String serializeCitations(QaQuestionResponse response) {
    if (response == null || response.citations() == null || response.citations().isEmpty()) {
      return null;
    }
    try {
      return mapper.writeValueAsString(response.citations());
    } catch (Exception ex) {
      log.warn("Failed to serialize QA citations for chat history", ex);
      return null;
    }
  }

  private List<Citation> parseCitations(String rawJson) {
    if (!StringUtils.hasText(rawJson)) {
      return List.of();
    }
    try {
      List<Citation> citations = mapper.readValue(rawJson, CITATION_LIST_TYPE);
      return citations != null ? citations : List.of();
    } catch (Exception ex) {
      log.warn("Failed to parse QA conversation citations payload", ex);
      return List.of();
    }
  }

  private static String normalizeRole(String rawRole) {
    if ("assistant".equalsIgnoreCase(rawRole)) {
      return "assistant";
    }
    return "user";
  }

  private static UUID parseUuid(String raw) {
    if (!StringUtils.hasText(raw)) {
      return null;
    }
    try {
      return UUID.fromString(raw.trim());
    } catch (Exception ex) {
      return null;
    }
  }

  private static String normalizeScope(String chatScopeId, String imageRef) {
    if (StringUtils.hasText(chatScopeId)) {
      return truncate(chatScopeId.trim(), CHAT_SCOPE_MAX_LEN);
    }
    if (StringUtils.hasText(imageRef)) {
      return truncate("image|" + imageRef.trim().toLowerCase(Locale.ROOT), CHAT_SCOPE_MAX_LEN);
    }
    return DEFAULT_SCOPE;
  }

  private static String buildConversationTitle(String prompt) {
    if (!StringUtils.hasText(prompt)) {
      return "New chat";
    }
    return truncate(prompt.trim(), TITLE_MAX_LEN);
  }

  private static String truncate(String value, int maxLen) {
    if (!StringUtils.hasText(value)) {
      return value;
    }
    if (value.length() <= maxLen) {
      return value;
    }
    return value.substring(0, maxLen);
  }

  private static String normalizeText(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }

  private static Instant toInstant(OffsetDateTime dateTime) {
    return dateTime != null ? dateTime.toInstant() : null;
  }

  private record ConversationRow(
      UUID conversationId,
      String chatScopeId,
      String title,
      String imageRef,
      Instant createdAt,
      Instant updatedAt) {}

  private record QaConversationMessageRow(
      Long id,
      UUID conversationId,
      String role,
      String content,
      String citationsJson,
      Instant createdAt) {}
}
