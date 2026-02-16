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
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography
} from "@mui/material";
import ShieldIcon from "@mui/icons-material/Shield";
import InsightsIcon from "@mui/icons-material/Insights";
import OpenInNewRoundedIcon from "@mui/icons-material/OpenInNewRounded";
import RocketLaunchIcon from "@mui/icons-material/RocketLaunch";
import VerifiedRoundedIcon from "@mui/icons-material/VerifiedRounded";
import WarningAmberRoundedIcon from "@mui/icons-material/WarningAmberRounded";
import { Link as RouterLink, useSearchParams } from "react-router-dom";
import { api } from "../api/client";
import type { AssessImageResponse, RiskBand } from "../api/types";
import { AssessmentFollowUpChat } from "../components/AssessmentFollowUpChat";
import { PageHeader } from "../components/PageHeader";
import { JsonPanel } from "../components/JsonPanel";
import { useRecentActivity } from "../hooks/useRecentActivity";

function bandColor(band?: RiskBand) {
  switch (band) {
    case "CRITICAL":
      return "error";
    case "HIGH":
      return "warning";
    case "MEDIUM":
      return "info";
    case "LOW":
      return "success";
    default:
      return "default";
  }
}

function buildImageAssessmentContext(result: AssessImageResponse): string {
  const lines: string[] = [];
  lines.push("Assessment type: image");
  lines.push(`Image reference: ${result.imageRef || "Unknown"}`);
  lines.push(`Risk band: ${result.band || "Unknown"}`);
  lines.push(`Risk score: ${result.overallRisk ?? "Unknown"}`);
  if (result.explanation) {
    lines.push(`Assessment explanation: ${result.explanation}`);
  }

  const findings = (result.topFindings || []).slice(0, 12);
  if (findings.length === 0) {
    lines.push("Top findings: none");
  } else {
    lines.push("Top findings:");
    findings.forEach((finding, index) => {
      lines.push(
        `${index + 1}. ${finding.cveId || "Unknown CVE"} | CVSS ${finding.cvss ?? "n/a"} | EPSS ${finding.epss ?? "n/a"} | Fix ${finding.fixAvailable ? "available" : "not available"}`
      );
      if (finding.summary) {
        lines.push(`   Summary: ${finding.summary}`);
      }
      if ((finding.packages || []).length > 0) {
        lines.push(`   Packages: ${(finding.packages || []).slice(0, 8).join(", ")}`);
      }
    });
  }

  const citations = (result.citations || []).slice(0, 10);
  if (citations.length > 0) {
    lines.push("Citations:");
    citations.forEach((citation, index) => {
      lines.push(`${index + 1}. ${citation.cveId || "Evidence"}${citation.url ? ` (${citation.url})` : ""}`);
      if (citation.snippet) {
        lines.push(`   Snippet: ${citation.snippet}`);
      }
    });
  }

  return lines.join("\n");
}

function buildImageChatScopeId(result: AssessImageResponse): string {
  const normalizedImageRef = (result.imageRef || "unknown").trim().toLowerCase();
  return `image|${normalizedImageRef || "unknown"}`;
}

function buildImageChatScopeIdFromImageRef(imageRef: string): string {
  const normalized = (imageRef || "").trim().toLowerCase();
  if (!normalized) {
    return "";
  }
  return `image|${normalized}`;
}

export function ImageAssessment() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [imageRef, setImageRef] = React.useState(searchParams.get("imageRef") || "");
  const [kValue, setKValue] = React.useState(searchParams.get("k") || "6");
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [result, setResult] = React.useState<AssessImageResponse | null>(null);
  const [showRaw, setShowRaw] = React.useState(false);
  const { addActivity } = useRecentActivity();

  React.useEffect(() => {
    setImageRef(searchParams.get("imageRef") || "");
    setKValue(searchParams.get("k") || "6");
  }, [searchParams]);

  const handleSubmit = async (event?: React.FormEvent) => {
    event?.preventDefault();
    if (!imageRef.trim()) {
      setError("Image reference is required.");
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const trimmedK = kValue.trim();
      const kParsed = Number(trimmedK);
      const payload = {
        imageRef: imageRef.trim(),
        k: trimmedK && Number.isFinite(kParsed) ? kParsed : undefined
      };
      const data = await api.assessImage(payload);
      setResult(data);
      const nextParams = new URLSearchParams(searchParams);
      nextParams.set("imageRef", payload.imageRef);
      if (payload.k !== undefined) {
        nextParams.set("k", String(payload.k));
      } else {
        nextParams.delete("k");
      }
      setSearchParams(nextParams, { replace: true });
      addActivity({
        kind: "ASSESS_IMAGE",
        label: payload.imageRef,
        description: data.band ? `Band: ${data.band}` : "Image assessment",
        href: `/assess?imageRef=${encodeURIComponent(payload.imageRef)}${payload.k !== undefined ? `&k=${payload.k}` : ""}`
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Request failed");
    } finally {
      setLoading(false);
    }
  };

  const normalizedRisk =
    typeof result?.overallRisk === "number"
      ? Math.max(0, Math.min(100, result.overallRisk <= 1 ? result.overallRisk * 100 : result.overallRisk))
      : null;

  return (
    <Stack spacing={3}>
      <PageHeader
        title="Image Assessment"
        subtitle="Run a full assessment (scan + enrichment + risk band) and review evidence."
        icon={<ShieldIcon sx={{ color: "var(--mint-500)" }} />}
      />

      <Paper className="section-card">
        <Box component="form" onSubmit={handleSubmit}>
          <Stack spacing={2} sx={{ p: 3 }}>
            <Grid container spacing={2} alignItems="center">
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Image reference"
                  placeholder="nginx:1.25"
                  value={imageRef}
                  onChange={(event) => setImageRef(event.target.value)}
                  helperText="Examples: nginx:1.25, alpine:3.19, ghcr.io/org/image:tag"
                />
              </Grid>
              <Grid item xs={12} md={2}>
                <TextField
                  fullWidth
                  label="Top K"
                  type="number"
                  value={kValue}
                  onChange={(event) => setKValue(event.target.value)}
                  helperText="Top findings"
                />
              </Grid>
              <Grid item xs={12} md={4}>
                <Button
                  variant="contained"
                  type="submit"
                  disabled={loading}
                  startIcon={<RocketLaunchIcon />}
                  endIcon={loading ? <CircularProgress size={18} color="inherit" /> : undefined}
                >
                  {loading ? "Assessing…" : "Run assessment"}
                </Button>
              </Grid>
            </Grid>
            {error && <Alert severity="error">{error}</Alert>}
          </Stack>
        </Box>
      </Paper>

      <Box>
        <AssessmentFollowUpChat
          chatScopeId={result ? buildImageChatScopeId(result) : buildImageChatScopeIdFromImageRef(imageRef)}
          assessmentContext={result ? buildImageAssessmentContext(result) : ""}
          imageRef={imageRef}
          title="Assessment follow-up chat"
        />
      </Box>

      {result && (
        <Paper className="section-card">
          <Stack spacing={2} sx={{ p: 3 }}>
            <Stack direction={{ xs: "column", md: "row" }} spacing={2} alignItems={{ xs: "flex-start", md: "center" }}>
              <Typography variant="h6" sx={{ fontWeight: 750 }}>
                Assessment result
              </Typography>
              <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                <Chip label={result.band || "Unknown"} color={bandColor(result.band)} />
                {normalizedRisk !== null && (
                  <Chip label={`Risk score: ${normalizedRisk.toFixed(0)}/100`} variant="outlined" />
                )}
                {result.imageRef && <Chip label={result.imageRef} variant="outlined" />}
              </Stack>
            </Stack>

            {result.explanation && <Typography color="text.secondary">{result.explanation}</Typography>}

            <Grid container spacing={2}>
              <Grid item xs={12} md={5}>
                <Paper variant="outlined" sx={{ p: 2.5, borderRadius: 3 }}>
                  <Stack spacing={1.5}>
                    <Stack direction="row" spacing={1} alignItems="center">
                      {result.band === "LOW" || result.band === "MEDIUM" ? (
                        <VerifiedRoundedIcon sx={{ color: "var(--mint-500)" }} />
                      ) : (
                        <WarningAmberRoundedIcon sx={{ color: "var(--amber-500)" }} />
                      )}
                      <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                        Summary
                      </Typography>
                    </Stack>
                    <Typography variant="body2" color="text.secondary">
                      Review top findings and citations below. Use the raw JSON panel for auditability.
                    </Typography>
                    <Stack direction="row" spacing={1} flexWrap="wrap">
                      <Chip
                        label={`${(result.topFindings || []).length} findings`}
                        size="small"
                        variant="outlined"
                      />
                      <Chip
                        label={`${(result.citations || []).length} citations`}
                        size="small"
                        variant="outlined"
                      />
                    </Stack>
                  </Stack>
                </Paper>
              </Grid>
              <Grid item xs={12} md={7}>
                <Paper variant="outlined" sx={{ p: 2.5, borderRadius: 3 }}>
                  <Stack spacing={1.5}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                      Top findings
                    </Typography>
                    {(result.topFindings || []).length === 0 ? (
                      <Typography variant="body2" color="text.secondary">
                        No findings were returned for this request.
                      </Typography>
                    ) : (
                      <Box sx={{ overflowX: "auto" }}>
                        <Table size="small" sx={{ minWidth: 640 }}>
                          <TableHead>
                            <TableRow>
                              <TableCell sx={{ fontWeight: 700 }}>CVE</TableCell>
                              <TableCell sx={{ fontWeight: 700 }}>Summary</TableCell>
                              <TableCell sx={{ fontWeight: 700 }} align="right">
                                CVSS
                              </TableCell>
                              <TableCell sx={{ fontWeight: 700 }} align="right">
                                EPSS
                              </TableCell>
                              <TableCell sx={{ fontWeight: 700 }} align="right">
                                Fix
                              </TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {(result.topFindings || []).map((finding) => (
                              <TableRow key={finding.cveId || finding.summary}>
                                <TableCell sx={{ whiteSpace: "nowrap" }}>
                                  {finding.cveId ? (
                                    <Link
                                      component={RouterLink}
                                      to={`/cves?cveId=${encodeURIComponent(finding.cveId)}`}
                                      underline="hover"
                                      sx={{ fontWeight: 700 }}
                                    >
                                      {finding.cveId}
                                    </Link>
                                  ) : (
                                    <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                      —
                                    </Typography>
                                  )}
                                </TableCell>
                                <TableCell>
                                  <Typography variant="body2" color="text.secondary" noWrap>
                                    {finding.summary || "—"}
                                  </Typography>
                                  {finding.url && (
                                    <Link href={finding.url} target="_blank" rel="noreferrer" underline="hover">
                                      <Stack direction="row" spacing={0.5} alignItems="center">
                                        <Typography variant="caption" color="text.secondary" noWrap>
                                          Source
                                        </Typography>
                                        <OpenInNewRoundedIcon sx={{ fontSize: 14, color: "text.secondary" }} />
                                      </Stack>
                                    </Link>
                                  )}
                                </TableCell>
                                <TableCell align="right">
                                  {finding.cvss !== undefined ? (
                                    <Chip label={finding.cvss} size="small" />
                                  ) : (
                                    <Typography variant="body2" color="text.secondary">
                                      —
                                    </Typography>
                                  )}
                                </TableCell>
                                <TableCell align="right">
                                  {finding.epss !== undefined ? (
                                    <Chip label={finding.epss} size="small" variant="outlined" />
                                  ) : (
                                    <Typography variant="body2" color="text.secondary">
                                      —
                                    </Typography>
                                  )}
                                </TableCell>
                                <TableCell align="right">
                                  {finding.fixAvailable ? (
                                    <Chip label="Yes" size="small" color="success" />
                                  ) : (
                                    <Chip label="No" size="small" variant="outlined" />
                                  )}
                                </TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </Box>
                    )}
                  </Stack>
                </Paper>
              </Grid>
            </Grid>

            {(result.citations || []).length > 0 && (
              <Paper variant="outlined" sx={{ p: 2.5, borderRadius: 3 }}>
                <Stack spacing={1.5}>
                  <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                    Citations
                  </Typography>
                  <Grid container spacing={2}>
                    {(result.citations || []).map((citation, index) => (
                      <Grid item xs={12} md={6} key={`${citation.cveId || "citation"}-${index}`}>
                        <Paper variant="outlined" sx={{ p: 2, borderRadius: 3, height: "100%" }}>
                          <Stack spacing={1}>
                            <Stack direction="row" spacing={1} alignItems="center">
                              <InsightsIcon sx={{ color: "var(--amber-500)" }} />
                              <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                                {citation.cveId || "Evidence"}
                              </Typography>
                            </Stack>
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
                      </Grid>
                    ))}
                  </Grid>
                </Stack>
              </Paper>
            )}

            <Box>
              <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
                <Button variant="outlined" size="small" onClick={() => setShowRaw(!showRaw)}>
                  {showRaw ? "Hide raw JSON" : "Show raw JSON"}
                </Button>
              </Stack>
              {showRaw && <JsonPanel title="Assessment payload" data={result} />}
            </Box>
          </Stack>
        </Paper>
      )}
    </Stack>
  );
}
