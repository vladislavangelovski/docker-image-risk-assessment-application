import React from "react";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Chip,
  Grid,
  Link,
  Paper,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography
} from "@mui/material";
import PsychologyIcon from "@mui/icons-material/Psychology";
import FactCheckIcon from "@mui/icons-material/FactCheck";
import OpenInNewRoundedIcon from "@mui/icons-material/OpenInNewRounded";
import ContentCopyRoundedIcon from "@mui/icons-material/ContentCopyRounded";
import RocketLaunchIcon from "@mui/icons-material/RocketLaunch";
import HistoryRoundedIcon from "@mui/icons-material/HistoryRounded";
import RefreshRoundedIcon from "@mui/icons-material/RefreshRounded";
import { useSearchParams } from "react-router-dom";
import { api } from "../api/client";
import type { QaChatHistoryItem, QaClaimResponse, QaQuestionResponse, Verdict } from "../api/types";
import { PageHeader } from "../components/PageHeader";
import { JsonPanel } from "../components/JsonPanel";
import { useRecentActivity } from "../hooks/useRecentActivity";
import { copyText } from "../utils/clipboard";
import { formatRelativeTime } from "../utils/time";

function verdictColor(verdict?: Verdict) {
  switch (verdict) {
    case "SUPPORTS":
      return "success";
    case "REFUTES":
      return "error";
    case "INSUFFICIENT":
      return "warning";
    default:
      return "default";
  }
}

export function QaCenter() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [tab, setTab] = React.useState(0);

  const [question, setQuestion] = React.useState("");
  const [questionImageRef, setQuestionImageRef] = React.useState("");
  const [questionK, setQuestionK] = React.useState("4");
  const [questionLoading, setQuestionLoading] = React.useState(false);
  const [questionError, setQuestionError] = React.useState<string | null>(null);
  const [questionResult, setQuestionResult] = React.useState<QaQuestionResponse | null>(null);
  const [showQuestionRaw, setShowQuestionRaw] = React.useState(false);

  const [claim, setClaim] = React.useState("");
  const [claimImageRef, setClaimImageRef] = React.useState("");
  const [claimTopK, setClaimTopK] = React.useState("4");
  const [claimLoading, setClaimLoading] = React.useState(false);
  const [claimError, setClaimError] = React.useState<string | null>(null);
  const [claimResult, setClaimResult] = React.useState<QaClaimResponse | null>(null);
  const [showClaimRaw, setShowClaimRaw] = React.useState(false);
  const [copied, setCopied] = React.useState(false);
  const { addActivity } = useRecentActivity();
  const [historyItems, setHistoryItems] = React.useState<QaChatHistoryItem[]>([]);
  const [historyLoading, setHistoryLoading] = React.useState(false);
  const [historyError, setHistoryError] = React.useState<string | null>(null);

  const refreshHistory = React.useCallback(async () => {
    setHistoryLoading(true);
    setHistoryError(null);
    try {
      const data = await api.qaHistory(50);
      setHistoryItems(data || []);
    } catch (err) {
      setHistoryError(err instanceof Error ? err.message : "Unable to load chat history");
    } finally {
      setHistoryLoading(false);
    }
  }, []);

  React.useEffect(() => {
    const tabParam = searchParams.get("tab");
    setTab(tabParam === "claim" ? 1 : 0);

    setQuestion(searchParams.get("question") || "");
    setQuestionImageRef(searchParams.get("imageRef") || "");
    setQuestionK(searchParams.get("k") || "4");

    setClaim(searchParams.get("claim") || "");
    setClaimImageRef(searchParams.get("imageRef") || "");
    setClaimTopK(searchParams.get("topK") || "4");
  }, [searchParams]);

  React.useEffect(() => {
    void refreshHistory();
  }, [refreshHistory]);

  const handleQuestionSubmit = async () => {
    if (!question.trim()) {
      setQuestionError("Question text is required.");
      return;
    }
    setQuestionError(null);
    setQuestionLoading(true);
    try {
      const trimmedK = questionK.trim();
      const kParsed = Number(trimmedK);
      const data = await api.qaQuestion({
        question: question.trim(),
        imageRef: questionImageRef.trim() || undefined,
        k: trimmedK && Number.isFinite(kParsed) ? kParsed : undefined
      });
      setQuestionResult(data);
      const nextParams = new URLSearchParams(searchParams);
      nextParams.set("tab", "question");
      nextParams.set("question", question.trim());
      if (questionImageRef.trim()) {
        nextParams.set("imageRef", questionImageRef.trim());
      } else {
        nextParams.delete("imageRef");
      }
      if (trimmedK && Number.isFinite(kParsed)) {
        nextParams.set("k", String(kParsed));
      } else {
        nextParams.delete("k");
      }
      nextParams.delete("claim");
      nextParams.delete("topK");
      setSearchParams(nextParams, { replace: true });

      const short = question.trim().length > 80 ? `${question.trim().slice(0, 77)}…` : question.trim();
      addActivity({
        kind: "QA_QUESTION",
        label: short,
        description: questionImageRef.trim() ? `Image: ${questionImageRef.trim()}` : "Question",
        href: `/qa?tab=question&question=${encodeURIComponent(question.trim())}${
          questionImageRef.trim() ? `&imageRef=${encodeURIComponent(questionImageRef.trim())}` : ""
        }${trimmedK && Number.isFinite(kParsed) ? `&k=${kParsed}` : ""}`
      });
      void refreshHistory();
    } catch (err) {
      setQuestionError(err instanceof Error ? err.message : "Request failed");
    } finally {
      setQuestionLoading(false);
    }
  };

  const handleClaimSubmit = async () => {
    if (!claim.trim()) {
      setClaimError("Claim text is required.");
      return;
    }
    setClaimError(null);
    setClaimLoading(true);
    try {
      const trimmedK = claimTopK.trim();
      const kParsed = Number(trimmedK);
      const data = await api.qaClaim({
        claim: claim.trim(),
        imageRef: claimImageRef.trim() || undefined,
        topK: trimmedK && Number.isFinite(kParsed) ? kParsed : undefined
      });
      setClaimResult(data);

      const nextParams = new URLSearchParams(searchParams);
      nextParams.set("tab", "claim");
      nextParams.set("claim", claim.trim());
      if (claimImageRef.trim()) {
        nextParams.set("imageRef", claimImageRef.trim());
      } else {
        nextParams.delete("imageRef");
      }
      if (trimmedK && Number.isFinite(kParsed)) {
        nextParams.set("topK", String(kParsed));
      } else {
        nextParams.delete("topK");
      }
      nextParams.delete("question");
      nextParams.delete("k");
      setSearchParams(nextParams, { replace: true });

      const short = claim.trim().length > 80 ? `${claim.trim().slice(0, 77)}…` : claim.trim();
      addActivity({
        kind: "QA_CLAIM",
        label: short,
        description: claimImageRef.trim() ? `Image: ${claimImageRef.trim()}` : "Claim",
        href: `/qa?tab=claim&claim=${encodeURIComponent(claim.trim())}${
          claimImageRef.trim() ? `&imageRef=${encodeURIComponent(claimImageRef.trim())}` : ""
        }${trimmedK && Number.isFinite(kParsed) ? `&topK=${kParsed}` : ""}`
      });
      void refreshHistory();
    } catch (err) {
      setClaimError(err instanceof Error ? err.message : "Request failed");
    } finally {
      setClaimLoading(false);
    }
  };

  const handleCopyAnswer = async () => {
    const text = tab === 0 ? questionResult?.answer : claimResult?.rationale;
    if (!text) return;
    const ok = await copyText(text);
    if (ok) {
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1200);
    }
  };

  const openHistoryItem = (item: QaChatHistoryItem) => {
    if (item.kind === "QUESTION") {
      setTab(0);
      setQuestion(item.prompt || "");
      setQuestionImageRef(item.imageRef || "");
      setQuestionK(item.k != null ? String(item.k) : "4");
      setQuestionResult(item.questionResponse ?? null);
      setQuestionError(null);
      setShowQuestionRaw(false);
    } else if (item.kind === "CLAIM") {
      setTab(1);
      setClaim(item.prompt || "");
      setClaimImageRef(item.imageRef || "");
      setClaimTopK(item.k != null ? String(item.k) : "4");
      setClaimResult(item.claimResponse ?? null);
      setClaimError(null);
      setShowClaimRaw(false);
    }
  };

  const historyMeta = (item: QaChatHistoryItem) => {
    const parts: string[] = [];
    parts.push(item.kind === "CLAIM" ? "Claim" : "Question");
    if (item.imageRef) {
      parts.push(`Image: ${item.imageRef}`);
    }
    if (item.k != null) {
      parts.push(`K=${item.k}`);
    }
    if (item.createdAt) {
      const ts = Date.parse(item.createdAt);
      if (!Number.isNaN(ts)) {
        parts.push(formatRelativeTime(ts));
      }
    }
    return parts.join(" • ");
  };

  return (
    <Stack spacing={3}>
      <PageHeader
        title="QA Center"
        subtitle="Ask questions or validate claims with evidence-backed citations."
        icon={<PsychologyIcon sx={{ color: "var(--mint-500)" }} />}
      />

      <Paper className="section-card">
        <Stack spacing={2} sx={{ p: 3 }}>
          <Tabs
            value={tab}
            onChange={(_, value) => setTab(value)}
          >
            <Tab label="Question" />
            <Tab label="Claim" />
          </Tabs>

      {tab === 0 && (
          <Stack spacing={2}>
            <Typography variant="h6" sx={{ fontWeight: 750 }}>
              Ask a question
            </Typography>
            <Grid container spacing={2}>
              <Grid item xs={12} md={7}>
                <TextField
                  fullWidth
                  multiline
                  minRows={3}
                  label="Question"
                  placeholder="Which vulnerabilities are most exploitable?"
                  value={question}
                  onChange={(event) => setQuestion(event.target.value)}
                />
              </Grid>
              <Grid item xs={12} md={3}>
                <TextField
                  fullWidth
                  label="Image reference (optional)"
                  placeholder="nginx:1.25"
                  value={questionImageRef}
                  onChange={(event) => setQuestionImageRef(event.target.value)}
                />
              </Grid>
              <Grid item xs={12} md={2}>
                <TextField
                  fullWidth
                  label="Top K"
                  type="number"
                  value={questionK}
                  onChange={(event) => setQuestionK(event.target.value)}
                />
              </Grid>
            </Grid>
            <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5} alignItems={{ xs: "stretch", sm: "center" }}>
              <Button
                variant="contained"
                onClick={handleQuestionSubmit}
                disabled={questionLoading}
                startIcon={<RocketLaunchIcon />}
                endIcon={questionLoading ? <CircularProgress size={18} color="inherit" /> : undefined}
              >
                {questionLoading ? "Thinking…" : "Submit question"}
              </Button>
              <Button
                variant="outlined"
                size="small"
                onClick={() => setShowQuestionRaw(!showQuestionRaw)}
                disabled={!questionResult}
              >
                {showQuestionRaw ? "Hide raw JSON" : "Show raw JSON"}
              </Button>
              <Button
                variant="text"
                size="small"
                startIcon={<ContentCopyRoundedIcon />}
                onClick={handleCopyAnswer}
                disabled={!questionResult?.answer}
              >
                {copied ? "Copied" : "Copy answer"}
              </Button>
            </Stack>
            {questionError && <Alert severity="error">{questionError}</Alert>}
            {questionResult && (
              <Box>
                <Stack spacing={2}>
                  <Paper variant="outlined" sx={{ p: 2.5, borderRadius: 3 }}>
                    <Stack spacing={1.5}>
                      <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
                        Answer
                      </Typography>
                      <Typography>{questionResult.answer || "—"}</Typography>
                      <Stack direction="row" spacing={1} flexWrap="wrap">
                        {(questionResult.usedCves || []).map((cve) => (
                          <Chip key={cve} label={cve} size="small" />
                        ))}
                        {(questionResult.usedPackages || []).map((pkg) => (
                          <Chip key={pkg} label={pkg} size="small" variant="outlined" />
                        ))}
                      </Stack>
                    </Stack>
                  </Paper>
                  {(questionResult.citations || []).length > 0 && (
                    <Paper variant="outlined" sx={{ p: 2.5, borderRadius: 3 }}>
                      <Stack spacing={1.5}>
                        <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
                          Citations
                        </Typography>
                        <Stack spacing={1}>
                          {(questionResult.citations || []).map((citation, index) => (
                            <Paper key={`${citation.cveId || "citation"}-${index}`} sx={{ p: 2, borderRadius: 3 }}>
                              <Stack spacing={0.75}>
                                <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                                  {citation.cveId || "Evidence"}
                                </Typography>
                                {citation.snippet && (
                                  <Typography variant="body2" color="text.secondary">
                                    {citation.snippet}
                                  </Typography>
                                )}
                                {citation.url && (
                                  <Link href={citation.url} target="_blank" rel="noreferrer" underline="hover">
                                    <Stack direction="row" spacing={0.5} alignItems="center">
                                      <Typography variant="caption" color="text.secondary" noWrap>
                                        Open source
                                      </Typography>
                                      <OpenInNewRoundedIcon sx={{ fontSize: 14, color: "text.secondary" }} />
                                    </Stack>
                                  </Link>
                                )}
                              </Stack>
                            </Paper>
                          ))}
                        </Stack>
                      </Stack>
                    </Paper>
                  )}
                </Stack>
                {showQuestionRaw && <JsonPanel title="QA question payload" data={questionResult} />}
              </Box>
            )}
          </Stack>
      )}

      {tab === 1 && (
          <Stack spacing={2}>
            <Typography variant="h6" sx={{ fontWeight: 750 }}>
              Validate a claim
            </Typography>
            <Grid container spacing={2}>
              <Grid item xs={12} md={7}>
                <TextField
                  fullWidth
                  multiline
                  minRows={3}
                  label="Claim"
                  placeholder="This image contains only low-risk findings."
                  value={claim}
                  onChange={(event) => setClaim(event.target.value)}
                />
              </Grid>
              <Grid item xs={12} md={3}>
                <TextField
                  fullWidth
                  label="Image reference (optional)"
                  placeholder="nginx:1.25"
                  value={claimImageRef}
                  onChange={(event) => setClaimImageRef(event.target.value)}
                />
              </Grid>
              <Grid item xs={12} md={2}>
                <TextField
                  fullWidth
                  label="Top K"
                  type="number"
                  value={claimTopK}
                  onChange={(event) => setClaimTopK(event.target.value)}
                />
              </Grid>
            </Grid>
            <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5} alignItems={{ xs: "stretch", sm: "center" }}>
              <Button
                variant="contained"
                onClick={handleClaimSubmit}
                disabled={claimLoading}
                startIcon={<RocketLaunchIcon />}
                endIcon={claimLoading ? <CircularProgress size={18} color="inherit" /> : undefined}
              >
                {claimLoading ? "Evaluating…" : "Submit claim"}
              </Button>
              <Button
                variant="outlined"
                size="small"
                onClick={() => setShowClaimRaw(!showClaimRaw)}
                disabled={!claimResult}
              >
                {showClaimRaw ? "Hide raw JSON" : "Show raw JSON"}
              </Button>
              <Button
                variant="text"
                size="small"
                startIcon={<ContentCopyRoundedIcon />}
                onClick={handleCopyAnswer}
                disabled={!claimResult?.rationale}
              >
                {copied ? "Copied" : "Copy rationale"}
              </Button>
            </Stack>
            {claimError && <Alert severity="error">{claimError}</Alert>}
            {claimResult && (
              <Box>
                <Stack spacing={2}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <FactCheckIcon sx={{ color: "var(--amber-500)" }} />
                    <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
                      Verdict
                    </Typography>
                    <Chip label={claimResult.verdict || "Unknown"} color={verdictColor(claimResult.verdict)} />
                  </Stack>
                  <Typography color="text.secondary">{claimResult.rationale}</Typography>
                  <Stack spacing={1}>
                    {(claimResult.citations || []).map((citation, index) => (
                      <Paper key={`${citation.cveId}-${index}`} sx={{ p: 2, borderRadius: 3 }}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                          {citation.cveId}
                        </Typography>
                        {citation.snippet && (
                          <Typography variant="body2" color="text.secondary">
                            {citation.snippet}
                          </Typography>
                        )}
                        {citation.url && (
                          <Link href={citation.url} target="_blank" rel="noreferrer" underline="hover">
                            <Stack direction="row" spacing={0.5} alignItems="center">
                              <Typography variant="caption" color="text.secondary" noWrap>
                                Open source
                              </Typography>
                              <OpenInNewRoundedIcon sx={{ fontSize: 14, color: "text.secondary" }} />
                            </Stack>
                          </Link>
                        )}
                      </Paper>
                    ))}
                  </Stack>
                </Stack>
                {showClaimRaw && <JsonPanel title="QA claim payload" data={claimResult} />}
              </Box>
            )}
          </Stack>
      )}

        </Stack>
      </Paper>

      <Paper className="section-card">
        <Stack spacing={2} sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
            <Stack direction="row" spacing={1} alignItems="center">
              <HistoryRoundedIcon sx={{ color: "var(--mint-500)" }} />
              <Typography variant="h6" sx={{ fontWeight: 750 }}>
                Chat history
              </Typography>
            </Stack>
            <Button
              variant="text"
              size="small"
              startIcon={<RefreshRoundedIcon />}
              onClick={refreshHistory}
              disabled={historyLoading}
            >
              Refresh
            </Button>
          </Stack>
          {historyLoading && <Typography color="text.secondary">Loading history…</Typography>}
          {historyError && <Alert severity="warning">{historyError}</Alert>}
          {!historyLoading && historyItems.length === 0 && (
            <Typography color="text.secondary">No saved chats yet.</Typography>
          )}
          {historyItems.length > 0 && (
            <Stack spacing={1}>
              {historyItems.map((item) => {
                const label =
                  item.prompt && item.prompt.length > 120
                    ? `${item.prompt.slice(0, 117)}…`
                    : item.prompt || "Untitled";
                return (
                  <Paper
                    key={item.id ?? `${item.kind}-${label}`}
                    variant="outlined"
                    sx={{
                      p: 1.5,
                      borderRadius: 3,
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "space-between",
                      gap: 2
                    }}
                  >
                    <Box>
                      <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                        {label}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {historyMeta(item)}
                      </Typography>
                    </Box>
                    <Button variant="outlined" size="small" onClick={() => openHistoryItem(item)}>
                      Open
                    </Button>
                  </Paper>
                );
              })}
            </Stack>
          )}
        </Stack>
      </Paper>
    </Stack>
  );
}
