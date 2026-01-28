package com.finki.vladislavangelovski.ai_service.api;

import com.finki.vladislavangelovski.ai_service.history.QaChatHistoryService;
import com.finki.vladislavangelovski.common.dto.QaChatHistoryItem;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/qa/history")
public class QaHistoryController {
  private final QaChatHistoryService historyService;

  public QaHistoryController(QaChatHistoryService historyService) {
    this.historyService = historyService;
  }

  @GetMapping
  public List<QaChatHistoryItem> listHistory(
      @RequestHeader(value = "X-User-Id", required = false) String userId,
      @RequestHeader(value = "X-User-Name", required = false) String userName,
      @RequestHeader(value = "X-User-Email", required = false) String userEmail,
      @RequestParam(value = "limit", required = false) Integer limit) {
    return historyService.listHistory(userId, userName, userEmail, limit);
  }
}
