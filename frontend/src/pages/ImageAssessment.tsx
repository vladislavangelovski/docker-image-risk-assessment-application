import React from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  Grid,
  Paper,
  Stack,
  TextField,
  Typography
} from "@mui/material";
import ShieldIcon from "@mui/icons-material/Shield";
import InsightsIcon from "@mui/icons-material/Insights";
import { api } from "../api/client";
import type { AssessImageResponse, RiskBand } from "../api/types";
import { JsonPanel } from "../components/JsonPanel";

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

export function ImageAssessment() {
  const [imageRef, setImageRef] = React.useState("");
  const [kValue, setKValue] = React.useState("6");
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [result, setResult] = React.useState<AssessImageResponse | null>(null);
  const [showRaw, setShowRaw] = React.useState(false);

  const handleSubmit = async () => {
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
    } catch (err) {
      setError(err instanceof Error ? err.message : "Request failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Stack spacing={3}>
      <Paper className="section-card stagger-in" style={{ "--delay": 0 } as React.CSSProperties}>
        <Stack spacing={2} sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <ShieldIcon sx={{ color: "var(--mint-500)" }} />
            <Typography variant="h5">Image Risk Assessment</Typography>
          </Stack>
          <Typography color="text.secondary">
            Provide a container image reference and receive a risk band, explanation, and
            top findings with citations.
          </Typography>
          <Grid container spacing={2} alignItems="center">
            <Grid item xs={12} md={6}>
              <TextField
                fullWidth
                label="Image Reference"
                placeholder="nginx:1.25"
                value={imageRef}
                onChange={(event) => setImageRef(event.target.value)}
              />
            </Grid>
            <Grid item xs={12} md={2}>
              <TextField
                fullWidth
                label="Top K"
                type="number"
                value={kValue}
                onChange={(event) => setKValue(event.target.value)}
              />
            </Grid>
            <Grid item xs={12} md={4}>
              <Button variant="contained" onClick={handleSubmit} disabled={loading}>
                {loading ? "Assessing..." : "Run assessment"}
              </Button>
            </Grid>
          </Grid>
          {error && <Alert severity="error">{error}</Alert>}
        </Stack>
      </Paper>

      {result && (
        <Paper className="section-card stagger-in" style={{ "--delay": 1 } as React.CSSProperties}>
          <Stack spacing={2} sx={{ p: 3 }}>
            <Stack direction={{ xs: "column", md: "row" }} spacing={2} alignItems="center">
              <Typography variant="h6">Assessment Result</Typography>
              <Chip label={result.band || "Unknown"} color={bandColor(result.band)} />
              {typeof result.overallRisk === "number" && (
                <Chip label={`Risk score: ${result.overallRisk}`} variant="outlined" />
              )}
            </Stack>
            <Typography color="text.secondary">{result.explanation}</Typography>
            <Grid container spacing={2}>
              <Grid item xs={12} md={7}>
                <Stack spacing={2}>
                  <Typography variant="subtitle1">Top findings</Typography>
                  <Stack spacing={2}>
                    {(result.topFindings || []).map((finding) => (
                      <Paper key={finding.cveId} sx={{ p: 2, borderRadius: 3 }}>
                        <Stack spacing={1}>
                          <Stack direction="row" spacing={1} alignItems="center">
                            <InsightsIcon sx={{ color: "var(--amber-500)" }} />
                            <Typography variant="subtitle2">
                              {finding.cveId || "CVE"}
                            </Typography>
                            {finding.fixAvailable && (
                              <Chip label="Fix available" color="success" size="small" />
                            )}
                          </Stack>
                          <Typography variant="body2" color="text.secondary">
                            {finding.summary}
                          </Typography>
                          <Stack direction="row" spacing={1} flexWrap="wrap">
                            {finding.cvss !== undefined && (
                              <Chip label={`CVSS ${finding.cvss}`} size="small" />
                            )}
                            {finding.epss !== undefined && (
                              <Chip label={`EPSS ${finding.epss}`} size="small" />
                            )}
                            {finding.percentile !== undefined && (
                              <Chip label={`Percentile ${finding.percentile}`} size="small" />
                            )}
                          </Stack>
                          <Stack direction="row" spacing={1} flexWrap="wrap">
                            {(finding.packages || []).map((pkg) => (
                              <Chip key={pkg} label={pkg} size="small" variant="outlined" />
                            ))}
                          </Stack>
                          {finding.url && (
                            <Typography variant="caption" color="text.secondary">
                              {finding.url}
                            </Typography>
                          )}
                        </Stack>
                      </Paper>
                    ))}
                  </Stack>
                </Stack>
              </Grid>
              <Grid item xs={12} md={5}>
                <Stack spacing={2}>
                  <Typography variant="subtitle1">Citations</Typography>
                  <Stack spacing={1}>
                    {(result.citations || []).map((citation, index) => (
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
              </Grid>
            </Grid>
            <Box>
              <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
                <Button variant="outlined" size="small" onClick={() => setShowRaw(!showRaw)}>
                  {showRaw ? "Hide raw JSON" : "Show raw JSON"}
                </Button>
              </Stack>
              {showRaw && <JsonPanel data={result} />}
            </Box>
          </Stack>
        </Paper>
      )}
    </Stack>
  );
}
