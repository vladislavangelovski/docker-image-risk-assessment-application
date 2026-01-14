import React from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  Grid,
  Paper,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography
} from "@mui/material";
import PsychologyIcon from "@mui/icons-material/Psychology";
import FactCheckIcon from "@mui/icons-material/FactCheck";
import { api } from "../api/client";
import type { QaClaimResponse, QaQuestionResponse, Verdict } from "../api/types";
import { JsonPanel } from "../components/JsonPanel";

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
    } catch (err) {
      setClaimError(err instanceof Error ? err.message : "Request failed");
    } finally {
      setClaimLoading(false);
    }
  };

  return (
    <Stack spacing={3}>
      <Paper className="section-card stagger-in" style={{ "--delay": 0 } as React.CSSProperties}>
        <Stack spacing={2} sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <PsychologyIcon sx={{ color: "var(--mint-500)" }} />
            <Typography variant="h5">QA Intelligence Center</Typography>
          </Stack>
          <Typography color="text.secondary">
            Ask questions or validate claims with evidence-backed citations. Include an image
            reference to anchor answers to scan results.
          </Typography>
          <Tabs value={tab} onChange={(_, value) => setTab(value)}>
            <Tab label="Question" />
            <Tab label="Claim" />
          </Tabs>
        </Stack>
      </Paper>

      {tab === 0 && (
        <Paper className="section-card stagger-in" style={{ "--delay": 1 } as React.CSSProperties}>
          <Stack spacing={2} sx={{ p: 3 }}>
            <Typography variant="h6">Ask a question</Typography>
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
            <Stack direction="row" spacing={2} alignItems="center">
              <Button variant="contained" onClick={handleQuestionSubmit} disabled={questionLoading}>
                {questionLoading ? "Thinking..." : "Submit question"}
              </Button>
              <Button variant="outlined" size="small" onClick={() => setShowQuestionRaw(!showQuestionRaw)}>
                {showQuestionRaw ? "Hide raw JSON" : "Show raw JSON"}
              </Button>
            </Stack>
            {questionError && <Alert severity="error">{questionError}</Alert>}
            {questionResult && (
              <Box>
                <Stack spacing={2}>
                  <Typography variant="subtitle1">Answer</Typography>
                  <Typography>{questionResult.answer}</Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    {(questionResult.usedCves || []).map((cve) => (
                      <Chip key={cve} label={cve} size="small" />
                    ))}
                    {(questionResult.usedPackages || []).map((pkg) => (
                      <Chip key={pkg} label={pkg} size="small" variant="outlined" />
                    ))}
                  </Stack>
                  <Stack spacing={1}>
                    {(questionResult.citations || []).map((citation, index) => (
                      <Paper key={`${citation.cveId}-${index}`} sx={{ p: 2, borderRadius: 3 }}>
                        <Typography variant="subtitle2">{citation.cveId}</Typography>
                        <Typography variant="body2" color="text.secondary">
                          {citation.snippet}
                        </Typography>
                        {citation.url && (
                          <Typography variant="caption" color="text.secondary">
                            {citation.url}
                          </Typography>
                        )}
                      </Paper>
                    ))}
                  </Stack>
                </Stack>
                {showQuestionRaw && <JsonPanel data={questionResult} />}
              </Box>
            )}
          </Stack>
        </Paper>
      )}

      {tab === 1 && (
        <Paper className="section-card stagger-in" style={{ "--delay": 1 } as React.CSSProperties}>
          <Stack spacing={2} sx={{ p: 3 }}>
            <Typography variant="h6">Validate a claim</Typography>
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
            <Stack direction="row" spacing={2} alignItems="center">
              <Button variant="contained" onClick={handleClaimSubmit} disabled={claimLoading}>
                {claimLoading ? "Evaluating..." : "Submit claim"}
              </Button>
              <Button variant="outlined" size="small" onClick={() => setShowClaimRaw(!showClaimRaw)}>
                {showClaimRaw ? "Hide raw JSON" : "Show raw JSON"}
              </Button>
            </Stack>
            {claimError && <Alert severity="error">{claimError}</Alert>}
            {claimResult && (
              <Box>
                <Stack spacing={2}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <FactCheckIcon sx={{ color: "var(--amber-500)" }} />
                    <Typography variant="subtitle1">Verdict</Typography>
                    <Chip label={claimResult.verdict || "Unknown"} color={verdictColor(claimResult.verdict)} />
                  </Stack>
                  <Typography color="text.secondary">{claimResult.rationale}</Typography>
                  <Stack spacing={1}>
                    {(claimResult.citations || []).map((citation, index) => (
                      <Paper key={`${citation.cveId}-${index}`} sx={{ p: 2, borderRadius: 3 }}>
                        <Typography variant="subtitle2">{citation.cveId}</Typography>
                        <Typography variant="body2" color="text.secondary">
                          {citation.snippet}
                        </Typography>
                        {citation.url && (
                          <Typography variant="caption" color="text.secondary">
                            {citation.url}
                          </Typography>
                        )}
                      </Paper>
                    ))}
                  </Stack>
                </Stack>
                {showClaimRaw && <JsonPanel data={claimResult} />}
              </Box>
            )}
          </Stack>
        </Paper>
      )}
    </Stack>
  );
}
