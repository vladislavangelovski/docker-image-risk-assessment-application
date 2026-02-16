import React from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  FormControlLabel,
  Grid,
  Link,
  Paper,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography
} from "@mui/material";
import DescriptionRoundedIcon from "@mui/icons-material/DescriptionRounded";
import RocketLaunchIcon from "@mui/icons-material/RocketLaunch";
import OpenInNewRoundedIcon from "@mui/icons-material/OpenInNewRounded";
import { Link as RouterLink } from "react-router-dom";
import { api } from "../api/client";
import type { AssessComposeResponse, RiskBand } from "../api/types";
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

function severityChipColor(raw?: string) {
  const sev = (raw || "").toUpperCase();
  switch (sev) {
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

function normalizeRiskScore(score?: number): number | null {
  if (typeof score !== "number" || !Number.isFinite(score)) return null;
  const value = score <= 1 ? score * 100 : score;
  return Math.max(0, Math.min(100, value));
}

const severityOrder = ["CRITICAL", "HIGH", "MEDIUM", "LOW", "UNKNOWN"] as const;

function buildComposeAssessmentContext(result: AssessComposeResponse): string {
  const lines: string[] = [];
  lines.push("Assessment type: docker-compose");
  lines.push(`Risk band: ${result.band || "Unknown"}`);
  lines.push(`Risk score: ${result.overallRisk ?? "Unknown"}`);
  if (result.explanation) {
    lines.push(`Assessment explanation: ${result.explanation}`);
  }

  const configScan = result.configScan;
  if (configScan) {
    lines.push(
      `Config findings total: ${configScan.totalFindings ?? 0} | Config risk score: ${configScan.riskScore ?? 0}`
    );
    if (configScan.error) {
      lines.push(`Config scan error: ${configScan.error}`);
    }
    (configScan.findings || []).slice(0, 20).forEach((finding, index) => {
      lines.push(
        `Config finding ${index + 1}: ${finding.severity || "UNKNOWN"} ${finding.id || ""} ${finding.title || finding.message || ""}`.trim()
      );
      if (finding.resource) {
        lines.push(`   Location: ${finding.resource}`);
      }
    });
  }

  const services = result.services || [];
  if (services.length === 0) {
    lines.push("Services: none");
  } else {
    lines.push("Service image assessments:");
    services.slice(0, 20).forEach((service, index) => {
      const assessment = service.assessment;
      lines.push(
        `${index + 1}. Service ${service.serviceName || "unknown"} | Image ${service.imageRef || "none"} | Band ${assessment?.band || "n/a"} | Score ${assessment?.overallRisk ?? "n/a"}`
      );
      if (service.error) {
        lines.push(`   Error: ${service.error}`);
      }
      (assessment?.topFindings || []).slice(0, 5).forEach((finding) => {
        lines.push(
          `   - ${finding.cveId || "Unknown CVE"} | CVSS ${finding.cvss ?? "n/a"} | EPSS ${finding.epss ?? "n/a"} | Fix ${finding.fixAvailable ? "available" : "not available"}`
        );
      });
    });
  }

  return lines.join("\n");
}

function buildComposeChatScopeId(composeYaml: string): string {
  const normalized = composeYaml.trim();
  if (!normalized) {
    return "compose|empty";
  }

  let hash = 2166136261;
  for (let index = 0; index < normalized.length; index += 1) {
    hash ^= normalized.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return `compose|${(hash >>> 0).toString(16)}`;
}

export function ComposeAssessment() {
  const [composeYaml, setComposeYaml] = React.useState("");
  const [fileName, setFileName] = React.useState<string | null>(null);
  const [kValue, setKValue] = React.useState("6");
  const [scanImages, setScanImages] = React.useState(false);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [result, setResult] = React.useState<AssessComposeResponse | null>(null);
  const [chatScopeId, setChatScopeId] = React.useState<string | null>(null);
  const [showRaw, setShowRaw] = React.useState(false);
  const { addActivity } = useRecentActivity();

  const handleUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    try {
      const text = await file.text();
      setComposeYaml(text);
      setFileName(file.name);
      setError(null);
      setResult(null);
      setChatScopeId(null);
      setShowRaw(false);
    } catch {
      setError("Failed to read the selected file.");
    } finally {
      event.target.value = "";
    }
  };

  const handleSubmit = async (event?: React.FormEvent) => {
    event?.preventDefault();
    if (!composeYaml.trim()) {
      setError("docker-compose.yml content is required.");
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const composeSnapshot = composeYaml;
      const trimmedK = kValue.trim();
      const kParsed = Number(trimmedK);
      const payload = {
        composeYaml,
        k: scanImages && trimmedK && Number.isFinite(kParsed) ? kParsed : undefined,
        scanImages
      };
      const data = await api.assessCompose(payload);
      setResult(data);
      setChatScopeId(buildComposeChatScopeId(composeSnapshot));
      addActivity({
        kind: "ASSESS_COMPOSE",
        label: fileName || "docker-compose.yml",
        description: data.band ? `Band: ${data.band}` : "Compose assessment",
        href: "/assess/compose"
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Request failed");
    } finally {
      setLoading(false);
    }
  };

  const normalizedRisk = normalizeRiskScore(result?.overallRisk ?? undefined);
  const configScan = result?.configScan;
  const configSeverity = configScan?.severity || {};

  return (
    <Stack spacing={3}>
      <PageHeader
        title="Compose Assessment"
        subtitle="Upload a docker-compose.yml, scan for misconfigurations, and optionally assess referenced images."
        icon={<DescriptionRoundedIcon sx={{ color: "var(--mint-500)" }} />}
      />

      <Paper className="section-card">
        <Box component="form" onSubmit={handleSubmit}>
          <Stack spacing={2} sx={{ p: 3 }}>
            <Stack
              direction={{ xs: "column", md: "row" }}
              spacing={1.5}
              alignItems={{ xs: "stretch", md: "center" }}
            >
              <Button variant="outlined" component="label">
                Upload docker-compose.yml
                <input
                  hidden
                  type="file"
                  accept=".yml,.yaml,text/yaml,application/x-yaml"
                  onChange={handleUpload}
                />
              </Button>
              {fileName && <Chip label={fileName} variant="outlined" sx={{ alignSelf: "flex-start" }} />}
              <Box sx={{ flexGrow: 1 }} />
              <FormControlLabel
                control={
                  <Switch
                    checked={scanImages}
                    onChange={(event) => setScanImages(event.target.checked)}
                  />
                }
                label="Scan images"
              />
            </Stack>

            <Grid container spacing={2} alignItems="center">
              <Grid item xs={12} md={2}>
                <TextField
                  fullWidth
                  label="Top K"
                  type="number"
                  value={kValue}
                  onChange={(event) => setKValue(event.target.value)}
                  helperText={scanImages ? "Per image" : "Disabled"}
                  disabled={!scanImages}
                />
              </Grid>
              <Grid item xs={12} md={10}>
                <Button
                  variant="contained"
                  type="submit"
                  disabled={loading}
                  startIcon={<RocketLaunchIcon />}
                  endIcon={loading ? <CircularProgress size={18} color="inherit" /> : undefined}
                >
                  {loading ? "Assessing…" : "Run compose assessment"}
                </Button>
              </Grid>
            </Grid>

            {scanImages && (
              <Alert severity="info">
                Image scanning can take a few minutes on the first run (image pull + Trivy DB warmup).
                Subsequent scans are usually faster due to caching.
              </Alert>
            )}

            <TextField
              fullWidth
              label="docker-compose.yml content"
              placeholder={`services:\n  web:\n    image: nginx:1.25\n`}
              value={composeYaml}
              onChange={(event) => setComposeYaml(event.target.value)}
              multiline
              minRows={10}
            />

            {error && <Alert severity="error">{error}</Alert>}
          </Stack>
        </Box>
      </Paper>

      {result && (
        <Paper className="section-card">
          <Stack spacing={2} sx={{ p: 3 }}>
            <Stack
              direction={{ xs: "column", md: "row" }}
              spacing={2}
              alignItems={{ xs: "flex-start", md: "center" }}
            >
              <Typography variant="h6" sx={{ fontWeight: 750 }}>
                Assessment result
              </Typography>
              <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                <Chip label={result.band || "Unknown"} color={bandColor(result.band)} />
                {normalizedRisk !== null && (
                  <Chip label={`Risk score: ${normalizedRisk.toFixed(0)}/100`} variant="outlined" />
                )}
                <Chip
                  label={`${(result.services || []).length} services`}
                  variant="outlined"
                  size="small"
                />
                <Chip
                  label={`${configScan?.totalFindings ?? 0} config findings`}
                  variant="outlined"
                  size="small"
                />
              </Stack>
            </Stack>

            {result.explanation && <Typography color="text.secondary">{result.explanation}</Typography>}

            <Grid container spacing={2}>
              <Grid item xs={12} md={6}>
                <Paper variant="outlined" sx={{ p: 2.5, borderRadius: 3 }}>
                  <Stack spacing={1.5}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                      Compose config scan
                    </Typography>
                    {configScan?.error ? (
                      <Alert severity="warning">{configScan.error}</Alert>
                    ) : (
                      <Stack direction="row" spacing={1} flexWrap="wrap">
                        <Chip
                          label={`Config risk: ${normalizeRiskScore(configScan?.riskScore ?? 0)?.toFixed(0) ?? 0}/100`}
                          variant="outlined"
                          size="small"
                        />
                        {severityOrder.map((sev) =>
                          (configSeverity[sev] || 0) > 0 ? (
                            <Chip
                              key={sev}
                              label={`${sev}: ${configSeverity[sev]}`}
                              color={severityChipColor(sev)}
                              size="small"
                            />
                          ) : null
                        )}
                        {configScan?.scannerVersion && (
                          <Chip
                            label={configScan.scannerVersion}
                            variant="outlined"
                            size="small"
                          />
                        )}
                      </Stack>
                    )}

                    {(configScan?.findings || []).length === 0 ? (
                      <Typography variant="body2" color="text.secondary">
                        No misconfiguration findings were returned for this compose file.
                      </Typography>
                    ) : (
                      <Box sx={{ overflowX: "auto" }}>
                        <Table size="small" sx={{ minWidth: 720 }}>
                          <TableHead>
                            <TableRow>
                              <TableCell sx={{ fontWeight: 700 }}>Severity</TableCell>
                              <TableCell sx={{ fontWeight: 700 }}>ID</TableCell>
                              <TableCell sx={{ fontWeight: 700 }}>Title</TableCell>
                              <TableCell sx={{ fontWeight: 700 }}>Location</TableCell>
                              <TableCell sx={{ fontWeight: 700 }}>Link</TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {(configScan?.findings || []).map((finding, index) => (
                              <TableRow key={`${finding.id || "finding"}-${index}`}>
                                <TableCell>
                                  <Chip
                                    label={finding.severity || "UNKNOWN"}
                                    size="small"
                                    color={severityChipColor(finding.severity)}
                                  />
                                </TableCell>
                                <TableCell sx={{ whiteSpace: "nowrap", fontWeight: 700 }}>
                                  {finding.id || "—"}
                                </TableCell>
                                <TableCell>
                                  <Typography variant="body2" color="text.secondary" noWrap>
                                    {finding.title || finding.message || "—"}
                                  </Typography>
                                </TableCell>
                                <TableCell>
                                  <Typography variant="caption" color="text.secondary" noWrap>
                                    {finding.resource || "—"}
                                    {typeof finding.startLine === "number" ? `:${finding.startLine}` : ""}
                                    {typeof finding.endLine === "number" ? `-${finding.endLine}` : ""}
                                  </Typography>
                                </TableCell>
                                <TableCell>
                                  {finding.primaryUrl ? (
                                    <Link
                                      href={finding.primaryUrl}
                                      target="_blank"
                                      rel="noreferrer"
                                      underline="hover"
                                    >
                                      <Stack direction="row" spacing={0.5} alignItems="center">
                                        <Typography variant="caption" color="text.secondary" noWrap>
                                          Open
                                        </Typography>
                                        <OpenInNewRoundedIcon
                                          sx={{ fontSize: 14, color: "text.secondary" }}
                                        />
                                      </Stack>
                                    </Link>
                                  ) : (
                                    <Typography variant="caption" color="text.secondary">
                                      —
                                    </Typography>
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

              <Grid item xs={12} md={6}>
                <Paper variant="outlined" sx={{ p: 2.5, borderRadius: 3 }}>
                  <Stack spacing={1.5}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                      Service images
                    </Typography>

                    {(result.services || []).length === 0 ? (
                      <Typography variant="body2" color="text.secondary">
                        No services were detected in this compose file.
                      </Typography>
                    ) : (
                      <Box sx={{ overflowX: "auto" }}>
                        <Table size="small" sx={{ minWidth: 720 }}>
                          <TableHead>
                            <TableRow>
                              <TableCell sx={{ fontWeight: 700 }}>Service</TableCell>
                              <TableCell sx={{ fontWeight: 700 }}>Image</TableCell>
                              <TableCell sx={{ fontWeight: 700 }}>Risk</TableCell>
                              <TableCell sx={{ fontWeight: 700 }}>Details</TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {(result.services || []).map((svc, index) => {
                              const score = normalizeRiskScore(svc.assessment?.overallRisk ?? undefined);
                              return (
                                <TableRow key={`${svc.serviceName || "service"}-${index}`}>
                                  <TableCell sx={{ fontWeight: 700 }}>
                                    {svc.serviceName || "—"}
                                  </TableCell>
                                  <TableCell sx={{ whiteSpace: "nowrap" }}>
                                    {svc.imageRef ? (
                                      <Chip label={svc.imageRef} size="small" variant="outlined" />
                                    ) : (
                                      <Typography variant="body2" color="text.secondary">
                                        —
                                      </Typography>
                                    )}
                                  </TableCell>
                                  <TableCell sx={{ whiteSpace: "nowrap" }}>
                                    {svc.assessment?.band ? (
                                      <Stack direction="row" spacing={1} alignItems="center">
                                        <Chip
                                          label={svc.assessment.band}
                                          size="small"
                                          color={bandColor(svc.assessment.band)}
                                        />
                                        {score !== null && (
                                          <Typography variant="caption" color="text.secondary">
                                            {score.toFixed(0)}/100
                                          </Typography>
                                        )}
                                      </Stack>
                                    ) : svc.error ? (
                                      <Typography variant="body2" color="text.secondary" noWrap>
                                        {svc.error}
                                      </Typography>
                                    ) : (
                                      <Typography variant="body2" color="text.secondary">
                                        —
                                      </Typography>
                                    )}
                                  </TableCell>
                                  <TableCell>
                                    {svc.imageRef ? (
                                      <Link
                                        component={RouterLink}
                                        to={`/assess?imageRef=${encodeURIComponent(svc.imageRef)}`}
                                        underline="hover"
                                      >
                                        View image assessment
                                      </Link>
                                    ) : (
                                      <Typography variant="body2" color="text.secondary">
                                        —
                                      </Typography>
                                    )}
                                  </TableCell>
                                </TableRow>
                              );
                            })}
                          </TableBody>
                        </Table>
                      </Box>
                    )}
                  </Stack>
                </Paper>
              </Grid>
            </Grid>

            <Box>
              <AssessmentFollowUpChat
                chatScopeId={chatScopeId || buildComposeChatScopeId(composeYaml)}
                assessmentContext={buildComposeAssessmentContext(result)}
                title="Compose follow-up chat"
              />
            </Box>

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
