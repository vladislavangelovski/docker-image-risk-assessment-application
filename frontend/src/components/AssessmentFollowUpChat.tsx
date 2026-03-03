import React from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Link,
  Paper,
  Stack,
  TextField,
  Typography
} from "@mui/material";
import PsychologyRoundedIcon from "@mui/icons-material/PsychologyRounded";
import DeleteOutlineRoundedIcon from "@mui/icons-material/DeleteOutlineRounded";
import SendRoundedIcon from "@mui/icons-material/SendRounded";
import OpenInNewRoundedIcon from "@mui/icons-material/OpenInNewRounded";
import type { Citation, QaChatTurn, QaConversationHistoryItem } from "../api/types";
import { api } from "../api/client";

type ChatRole = "user" | "assistant";

interface FollowUpMessage {
  id: string;
  role: ChatRole;
  content: string;
  citations?: Citation[];
  timestamp: number;
}

interface FollowUpConversation {
  id: string;
  title: string;
  createdAt: number;
  updatedAt: number;
  messages: FollowUpMessage[];
}

interface AssessmentFollowUpChatProps {
  chatScopeId: string;
  assessmentContext: string;
  imageRef?: string;
  title?: string;
}

const MAX_CONTEXT_CHARS = 8_000;
const MAX_HISTORY_TURNS = 12;
const FOLLOW_UP_K = 8;
const MAX_CONVERSATIONS_PER_SCOPE = 20;

function normalizeContext(context: string): string {
  const trimmed = context.trim();
  if (!trimmed) {
    return "No assessment context provided.";
  }
  if (trimmed.length <= MAX_CONTEXT_CHARS) {
    return trimmed;
  }
  return `${trimmed.slice(0, MAX_CONTEXT_CHARS - 3)}...`;
}

function toChatHistory(messages: FollowUpMessage[]): QaChatTurn[] {
  return messages.slice(-MAX_HISTORY_TURNS).map((message) => ({
    role: message.role,
    content: message.content
  }));
}

function truncateLabel(value: string, maxChars: number): string {
  if (value.length <= maxChars) {
    return value;
  }
  return `${value.slice(0, maxChars - 1)}…`;
}

function formatConversationTime(timestamp: number): string {
  try {
    return new Date(timestamp).toLocaleDateString(undefined, {
      month: "short",
      day: "numeric"
    });
  } catch {
    return "";
  }
}

function parseTimestamp(raw?: string): number {
  if (!raw) {
    return Date.now();
  }
  const parsed = Date.parse(raw);
  return Number.isFinite(parsed) ? parsed : Date.now();
}

function buildConversationTitle(messages: FollowUpMessage[], fallbackTitle?: string): string {
  const trimmedFallback = (fallbackTitle || "").trim();
  if (trimmedFallback) {
    return truncateLabel(trimmedFallback, 56);
  }
  const firstUserMessage = messages.find((message) => message.role === "user");
  if (!firstUserMessage?.content?.trim()) {
    return "New chat";
  }
  return truncateLabel(firstUserMessage.content.trim(), 56);
}

function mapHistoryItems(items: QaConversationHistoryItem[]): FollowUpConversation[] {
  const conversations = items.reduce<FollowUpConversation[]>((result, item) => {
    const conversationId = (item.conversationId || "").trim();
    if (!conversationId) {
      return result;
    }

    const messages = (item.messages || []).reduce<FollowUpMessage[]>((messageItems, message, index) => {
      const content = (message.content || "").trim();
      if (!content) {
        return messageItems;
      }
      const role: ChatRole = message.role === "assistant" ? "assistant" : "user";
      messageItems.push({
        id: message.id ? String(message.id) : `${conversationId}-${index}`,
        role,
        content,
        citations: Array.isArray(message.citations) ? message.citations : [],
        timestamp: parseTimestamp(message.createdAt)
      });
      return messageItems;
    }, []);

    result.push({
      id: conversationId,
      title: buildConversationTitle(messages, item.title),
      createdAt: parseTimestamp(item.createdAt),
      updatedAt: parseTimestamp(item.updatedAt),
      messages
    });
    return result;
  }, []);

  return conversations.sort((left, right) => right.updatedAt - left.updatedAt);
}

export function AssessmentFollowUpChat({
  chatScopeId,
  assessmentContext,
  imageRef,
  title = "Follow-up chat"
}: AssessmentFollowUpChatProps) {
  const [conversations, setConversations] = React.useState<FollowUpConversation[]>([]);
  const [activeConversationId, setActiveConversationId] = React.useState<string | null>(null);
  const [draft, setDraft] = React.useState("");
  const [loading, setLoading] = React.useState(false);
  const [historyLoading, setHistoryLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const endRef = React.useRef<HTMLDivElement | null>(null);
  const [effectiveChatScopeId, setEffectiveChatScopeId] = React.useState(chatScopeId);

  React.useEffect(() => {
    if (chatScopeId === effectiveChatScopeId) {
      return;
    }
    const handle = window.setTimeout(() => setEffectiveChatScopeId(chatScopeId), 350);
    return () => window.clearTimeout(handle);
  }, [chatScopeId, effectiveChatScopeId]);

  const activeConversation = React.useMemo(() => {
    if (!activeConversationId) {
      return null;
    }
    return conversations.find((conversation) => conversation.id === activeConversationId) || null;
  }, [activeConversationId, conversations]);

  const activeMessages = activeConversation?.messages || [];

  const refreshHistory = React.useCallback(
    async (preferredConversationId?: string, showSpinner = false) => {
      if (showSpinner) {
        setHistoryLoading(true);
      }

      try {
        const scope = effectiveChatScopeId.trim();
        const items = scope
          ? await api.qaHistory(scope, MAX_CONVERSATIONS_PER_SCOPE)
          : await api.qaHistoryAll(MAX_CONVERSATIONS_PER_SCOPE);
        const mapped = mapHistoryItems(items || []);
        setConversations(mapped);
        setActiveConversationId((current) => {
          if (preferredConversationId && mapped.some((conversation) => conversation.id === preferredConversationId)) {
            return preferredConversationId;
          }
          if (current && mapped.some((conversation) => conversation.id === current)) {
            return current;
          }
          return mapped[0]?.id || null;
        });
      } catch (requestError) {
        setError(requestError instanceof Error ? requestError.message : "Unable to load chat history");
      } finally {
        if (showSpinner) {
          setHistoryLoading(false);
        }
      }
    },
    [effectiveChatScopeId]
  );

  React.useEffect(() => {
    setConversations([]);
    setActiveConversationId(null);
    setDraft("");
    setError(null);
    void refreshHistory(undefined, true);
  }, [effectiveChatScopeId, refreshHistory]);

  React.useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [activeConversationId, activeMessages.length, loading]);

  const submitQuestion = async (event?: React.FormEvent) => {
    event?.preventDefault();
    const question = draft.trim();
    if (!question) {
      return;
    }

    setError(null);
    setLoading(true);

    const selectedConversationId = activeConversation?.id;
    try {
      const response = await api.qaQuestion({
        question,
        imageRef: imageRef?.trim() || undefined,
        k: FOLLOW_UP_K,
        assessmentContext: normalizeContext(assessmentContext),
        chatHistory: toChatHistory(activeMessages),
        chatScopeId: chatScopeId.trim() || undefined,
        conversationId: selectedConversationId || undefined
      });

      setDraft("");
      await refreshHistory(response.conversationId || selectedConversationId || undefined);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Unable to answer follow-up");
    } finally {
      setLoading(false);
    }
  };

  const startConversation = () => {
    setActiveConversationId(null);
    setDraft("");
    setError(null);
  };

  const deleteConversation = async (conversationId: string) => {
    try {
      setError(null);
      setLoading(true);
      await api.qaDeleteConversation(conversationId);
      if (activeConversationId === conversationId) {
        setActiveConversationId(null);
      }
      await refreshHistory();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Unable to delete chat");
    } finally {
      setLoading(false);
    }
  };

  const deleteActiveConversation = async () => {
    if (!activeConversation?.id) {
      return;
    }
    await deleteConversation(activeConversation.id);
  };

  return (
    <Paper className="section-card" sx={{ p: 2.5 }}>
      <Stack spacing={2}>
        <Stack
          direction={{ xs: "column", sm: "row" }}
          spacing={1}
          alignItems={{ xs: "flex-start", sm: "center" }}
          justifyContent="space-between"
        >
          <Stack direction="row" spacing={1} alignItems="center">
            <PsychologyRoundedIcon sx={{ color: "var(--mint-500)" }} />
            <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
              {title}
            </Typography>
            {historyLoading && <CircularProgress size={16} />}
          </Stack>
          <Stack direction="row" spacing={1}>
            <Button variant="text" size="small" onClick={startConversation} disabled={loading || historyLoading}>
              New chat
            </Button>
            <Button
              variant="text"
              size="small"
              color="error"
              startIcon={<DeleteOutlineRoundedIcon />}
              onClick={deleteActiveConversation}
              disabled={loading || historyLoading || !activeConversation}
            >
              Delete chat
            </Button>
          </Stack>
        </Stack>

        <Stack spacing={1}>
          <Typography className="kicker" sx={{ fontSize: "0.64rem" }}>
            Saved threads
          </Typography>
          <Stack direction="row" spacing={1} flexWrap="wrap">
            {conversations.map((conversation) => (
              <Chip
                key={conversation.id}
                label={`${truncateLabel(conversation.title, 48)} · ${formatConversationTime(conversation.updatedAt)}`}
                onClick={() => setActiveConversationId(conversation.id)}
                onDelete={loading || historyLoading ? undefined : () => void deleteConversation(conversation.id)}
                color={conversation.id === activeConversationId ? "primary" : "default"}
                variant={conversation.id === activeConversationId ? "filled" : "outlined"}
                sx={{ mb: 1, borderRadius: 999 }}
              />
            ))}
          </Stack>
        </Stack>

        {!historyLoading && activeMessages.length === 0 && (
          <Typography variant="body2" color="text.secondary">
            Ask for remediation sequencing, rollout strategy, or risk clarification. Conversation
            history is stored server-side and can be reopened or deleted.
          </Typography>
        )}

        {activeMessages.length > 0 && (
          <Stack
            spacing={1.5}
            sx={{
              maxHeight: 420,
              overflowY: "auto",
              pr: 0.5,
              scrollbarGutter: "stable",
              maskImage: "linear-gradient(to bottom, transparent 0, black 14px, black calc(100% - 14px), transparent 100%)",
              WebkitMaskImage: "linear-gradient(to bottom, transparent 0, black 14px, black calc(100% - 14px), transparent 100%)"
            }}
          >
            {activeMessages.map((message, index) => {
              const isUser = message.role === "user";
              return (
                <Box
                  key={message.id}
                  sx={{
                    alignSelf: isUser ? "flex-end" : "flex-start",
                    maxWidth: { xs: "100%", sm: "86%" },
                    animation: "rise-in 320ms var(--ease-standard) both",
                    animationDelay: `${Math.min(index * 40, 200)}ms`
                  }}
                >
                  <Paper
                    className="surface-card"
                    sx={{
                      p: 1.5,
                      borderRadius: 2.8,
                      backgroundColor: isUser ? "rgba(168, 84, 47, 0.16)" : undefined
                    }}
                  >
                    <Typography
                      variant="caption"
                      sx={{ display: "block", mb: 0.75, fontWeight: 700, textTransform: "capitalize" }}
                      color="text.secondary"
                    >
                      {message.role}
                    </Typography>
                    <Typography variant="body2" sx={{ whiteSpace: "pre-wrap" }}>
                      {message.content}
                    </Typography>
                    {!isUser && (message.citations || []).length > 0 && (
                      <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ mt: 1 }}>
                        {(message.citations || []).slice(0, 6).map((citation, index) => (
                          <Chip
                            key={`${citation.cveId || "citation"}-${index}`}
                            label={citation.cveId || "Evidence"}
                            size="small"
                            variant="outlined"
                            component={citation.url ? Link : "div"}
                            href={citation.url}
                            target={citation.url ? "_blank" : undefined}
                            rel={citation.url ? "noreferrer" : undefined}
                            clickable={Boolean(citation.url)}
                            icon={citation.url ? <OpenInNewRoundedIcon sx={{ fontSize: 14 }} /> : undefined}
                          />
                        ))}
                      </Stack>
                    )}
                  </Paper>
                </Box>
              );
            })}
            <div ref={endRef} />
          </Stack>
        )}

        <Box component="form" onSubmit={submitQuestion}>
          <Stack spacing={1.5}>
            <TextField
              fullWidth
              multiline
              minRows={2}
              label="Ask a follow-up question"
              placeholder="Which fixes should ship first, and what rollback guardrails do you suggest?"
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              disabled={loading || historyLoading}
            />
            <Stack direction={{ xs: "column", sm: "row" }} spacing={1} alignItems={{ xs: "stretch", sm: "center" }}>
              <Button
                type="submit"
                variant="contained"
                disabled={loading || historyLoading || !draft.trim()}
                startIcon={<SendRoundedIcon />}
                endIcon={loading ? <CircularProgress size={16} color="inherit" /> : undefined}
                sx={{
                  transition:
                    "transform var(--motion-fast) var(--ease-standard), box-shadow var(--motion-fast) var(--ease-standard)"
                }}
              >
                {loading ? "Thinking…" : "Send"}
              </Button>
              <Typography variant="caption" color="text.secondary">
                Replies use this assessment context and prior thread history.
              </Typography>
            </Stack>
          </Stack>
        </Box>

        {error && <Alert severity="error">{error}</Alert>}
      </Stack>
    </Paper>
  );
}
