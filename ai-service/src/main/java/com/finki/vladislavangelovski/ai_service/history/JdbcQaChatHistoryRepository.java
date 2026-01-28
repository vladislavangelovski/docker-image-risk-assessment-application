package com.finki.vladislavangelovski.ai_service.history;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finki.vladislavangelovski.common.dto.QaChatHistoryItem;
import com.finki.vladislavangelovski.common.dto.QaChatKind;
import com.finki.vladislavangelovski.common.dto.QaClaimRequest;
import com.finki.vladislavangelovski.common.dto.QaClaimResponse;
import com.finki.vladislavangelovski.common.dto.QaQuestionRequest;
import com.finki.vladislavangelovski.common.dto.QaQuestionResponse;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcQaChatHistoryRepository implements QaChatHistoryRepository {
  private static final Logger log = LoggerFactory.getLogger(JdbcQaChatHistoryRepository.class);

  private static final String INSERT_SQL =
      """
          INSERT INTO qa_chat_history (
              user_id,
              user_name,
              chat_type,
              prompt,
              image_ref,
              top_k,
              response_json,
              created_at
          )
          VALUES (?, ?, ?, ?, ?, ?, ?, now())
          """;

  private static final String SELECT_SQL =
      """
          SELECT id,
                 chat_type,
                 prompt,
                 image_ref,
                 top_k,
                 response_json,
                 created_at
            FROM qa_chat_history
           WHERE user_id = ?
           ORDER BY created_at DESC
           LIMIT ?
          """;

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public JdbcQaChatHistoryRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Override
  public void saveQuestion(
      String userId, String userName, QaQuestionRequest request, QaQuestionResponse response) {
    save(
        userId,
        userName,
        QaChatKind.QUESTION,
        request.question(),
        request.imageRef(),
        request.k(),
        response);
  }

  @Override
  public void saveClaim(
      String userId, String userName, QaClaimRequest request, QaClaimResponse response) {
    save(
        userId,
        userName,
        QaChatKind.CLAIM,
        request.claim(),
        request.imageRef(),
        request.topK(),
        response);
  }

  @Override
  public List<QaChatHistoryItem> findRecentByUser(String userId, int limit) {
    return jdbc.query(SELECT_SQL, rowMapper(), userId, limit);
  }

  private void save(
      String userId,
      String userName,
      QaChatKind kind,
      String prompt,
      String imageRef,
      Integer k,
      Object response) {
    if (userId == null || userId.isBlank() || prompt == null || prompt.isBlank()) {
      return;
    }

    String payload;
    try {
      payload = mapper.writeValueAsString(response);
    } catch (JsonProcessingException ex) {
      log.warn("Failed to serialize QA history response; skipping history save", ex);
      return;
    }

    jdbc.update(
        INSERT_SQL,
        userId,
        userName,
        kind.name(),
        prompt.trim(),
        (imageRef == null || imageRef.isBlank()) ? null : imageRef.trim(),
        k,
        payload);
  }

  private RowMapper<QaChatHistoryItem> rowMapper() {
    return (ResultSet rs, int rowNum) -> {
      Long id = rs.getLong("id");
      String kindRaw = rs.getString("chat_type");
      QaChatKind kind = parseKind(kindRaw);
      String prompt = rs.getString("prompt");
      String imageRef = rs.getString("image_ref");
      Integer k = rs.getObject("top_k", Integer.class);
      String responseJson = rs.getString("response_json");
      OffsetDateTime createdAtRaw = rs.getObject("created_at", OffsetDateTime.class);
      Instant createdAt = createdAtRaw != null ? createdAtRaw.toInstant() : null;

      QaQuestionResponse questionResponse = null;
      QaClaimResponse claimResponse = null;

      if (responseJson != null && kind != null) {
        try {
          if (kind == QaChatKind.QUESTION) {
            questionResponse = mapper.readValue(responseJson, QaQuestionResponse.class);
          } else if (kind == QaChatKind.CLAIM) {
            claimResponse = mapper.readValue(responseJson, QaClaimResponse.class);
          }
        } catch (Exception ex) {
          log.warn("Failed to parse QA history response payload", ex);
        }
      }

      return new QaChatHistoryItem(
          id, kind, prompt, imageRef, k, questionResponse, claimResponse, createdAt);
    };
  }

  private static QaChatKind parseKind(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return QaChatKind.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}
