import React from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  Grid,
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
import SearchIcon from "@mui/icons-material/Search";
import ReceiptLongIcon from "@mui/icons-material/ReceiptLong";
import TuneRoundedIcon from "@mui/icons-material/TuneRounded";
import { useSearchParams } from "react-router-dom";
import { api } from "../api/client";
import type { ScanJobStatus, ScanResult } from "../api/types";
import { PageHeader } from "../components/PageHeader";
import { JsonPanel } from "../components/JsonPanel";
import { useRecentActivity } from "../hooks/useRecentActivity";

function isScanResult(value: unknown): value is ScanResult {
  if (!value || typeof value !== "object") return false;
  const record = value as Record<string, unknown>;
  return (
    typeof record.scanId === "string" ||
    typeof record.image === "string" ||
    Array.isArray(record.findings) ||
    typeof record.summary === "object"
  );
}

export function ScanViewer() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [scanId, setScanId] = React.useState(searchParams.get("scanId") || "");
  const [imageRef, setImageRef] = React.useState(searchParams.get("imageRef") || "");
  const [raw, setRaw] = React.useState(searchParams.get("raw") === "true" || searchParams.get("raw") === "1");
  const [result, setResult] = React.useState<ScanResult | unknown | null>(null);
  const [jobStatus, setJobStatus] = React.useState<ScanJobStatus | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [severityFilter, setSeverityFilter] = React.useState("ALL");
  const [textFilter, setTextFilter] = React.useState("");
  const { addActivity } = useRecentActivity();

  const resetErrors = () => {
    setError(null);
  };

  React.useEffect(() => {
    setScanId(searchParams.get("scanId") || "");
    setImageRef(searchParams.get("imageRef") || "");
    setRaw(searchParams.get("raw") === "true" || searchParams.get("raw") === "1");
  }, [searchParams]);

  const handleFetchById = async () => {
    if (!scanId.trim()) {
      setError("Scan ID is required.");
      return;
    }
    resetErrors();
    setLoading(true);
    try {
      const data = await api.scanById(scanId.trim(), raw);
      setResult(data);
      const nextParams = new URLSearchParams(searchParams);
      nextParams.set("scanId", scanId.trim());
      nextParams.delete("imageRef");
      nextParams.set("raw", raw ? "true" : "false");
      setSearchParams(nextParams, { replace: true });
      addActivity({
        kind: "SCAN_VIEW",
        label: `Scan ${scanId.trim()}`,
        description: raw ? "Raw output" : "Normalized output",
        href: `/scans?scanId=${encodeURIComponent(scanId.trim())}&raw=${raw ? "true" : "false"}`
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to fetch scan.");
    } finally {
      setLoading(false);
    }
  };

  const handleFetchLatest = async () => {
    if (!imageRef.trim()) {
      setError("Image reference is required.");
      return;
    }
    resetErrors();
    setLoading(true);
    try {
      const data = await api.scanLatestByImage(imageRef.trim(), raw);
      setResult(data);
      const nextParams = new URLSearchParams(searchParams);
      nextParams.set("imageRef", imageRef.trim());
      nextParams.delete("scanId");
      nextParams.set("raw", raw ? "true" : "false");
      setSearchParams(nextParams, { replace: true });
      addActivity({
        kind: "SCAN_VIEW",
        label: `Latest scan`,
        description: imageRef.trim(),
        href: `/scans?imageRef=${encodeURIComponent(imageRef.trim())}&raw=${raw ? "true" : "false"}`
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to fetch scan.");
    } finally {
      setLoading(false);
    }
  };

  const handleJobStatus = async () => {
    if (!scanId.trim()) {
      setError("Scan ID is required to check job status.");
      return;
    }
    resetErrors();
    setLoading(true);
    try {
      const status = await api.scanJobStatus(scanId.trim());
      setJobStatus(status);
      addActivity({
        kind: "SCAN_VIEW",
        label: `Job status`,
        description: scanId.trim(),
        href: `/scans?scanId=${encodeURIComponent(scanId.trim())}`
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to fetch job status.");
    } finally {
      setLoading(false);
    }
  };

  const normalized = isScanResult(result) ? result : null;
  const findings = normalized?.findings || [];
  const filteredFindings = findings.filter((finding) => {
    const sev = (finding.severity || "UNKNOWN").toUpperCase();
    const sevOk = severityFilter === "ALL" || sev === severityFilter;
    if (!sevOk) return false;

    const q = textFilter.trim().toLowerCase();
    if (!q) return true;
    return [
      finding.cveId,
      finding.packageName,
      finding.installedVersion,
      finding.fixedVersion,
      finding.sourceTarget
    ]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(q));
  });

  return (
    <Stack spacing={3}>
      <PageHeader
        title="Scan Viewer"
        subtitle="Inspect normalized scan results or raw Trivy output by scan ID or image reference."
        icon={<ReceiptLongIcon sx={{ color: "var(--mint-500)" }} />}
      />

      <Paper className="section-card">
        <Stack spacing={2} sx={{ p: 3 }}>
          <Grid container spacing={2} alignItems="center">
            <Grid item xs={12} md={5}>
              <TextField
                fullWidth
                label="Scan ID"
                placeholder="c9b3a6e3-5d7e-4a06-9c7a-0b2a9d347e77"
                value={scanId}
                onChange={(event) => setScanId(event.target.value)}
              />
            </Grid>
            <Grid item xs={12} md={5}>
              <TextField
                fullWidth
                label="Image Reference"
                placeholder="nginx:1.25"
                value={imageRef}
                onChange={(event) => setImageRef(event.target.value)}
              />
            </Grid>
            <Grid item xs={12} md={2}>
              <Stack direction="row" spacing={1} alignItems="center">
                <Switch checked={raw} onChange={() => setRaw(!raw)} />
                <Typography variant="caption">Raw</Typography>
              </Stack>
            </Grid>
          </Grid>
          <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
            <Button
              variant="contained"
              onClick={handleFetchById}
              disabled={loading}
              startIcon={<SearchIcon />}
            >
              Fetch by scan ID
            </Button>
            <Button variant="outlined" onClick={handleFetchLatest} disabled={loading}>
              Fetch latest by image
            </Button>
            <Button variant="outlined" onClick={handleJobStatus} disabled={loading}>
              Check job status
            </Button>
          </Stack>
          {error && <Alert severity="error">{error}</Alert>}
        </Stack>
      </Paper>

      {(result || jobStatus) && (
        <Paper className="section-card">
          <Stack spacing={2} sx={{ p: 3 }}>
            <Stack direction="row" spacing={1} alignItems="center">
              <Typography variant="h6">Scan Output</Typography>
              {jobStatus?.status && (
                <Chip label={`Job: ${jobStatus.status}`} variant="outlined" />
              )}
            </Stack>
            {jobStatus && (
              <Stack spacing={1}>
                <Typography variant="body2" color="text.secondary">
                  Job message: {jobStatus.message || "None"}
                </Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap">
                  {jobStatus.createdAt && <Chip label={`Created ${jobStatus.createdAt}`} size="small" />}
                  {jobStatus.startedAt && <Chip label={`Started ${jobStatus.startedAt}`} size="small" />}
                  {jobStatus.finishedAt && <Chip label={`Finished ${jobStatus.finishedAt}`} size="small" />}
                </Stack>
              </Stack>
            )}
            {normalized && normalized.summary && (
              <Paper variant="outlined" sx={{ p: 2.5, borderRadius: 3 }}>
                <Stack spacing={1.5}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <TuneRoundedIcon sx={{ color: "var(--amber-500)" }} />
                    <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
                      Summary
                    </Typography>
                  </Stack>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Chip label={`Total: ${normalized.summary.total}`} variant="outlined" size="small" />
                    <Chip label={`Fixes: ${normalized.summary.fixAvailable}`} variant="outlined" size="small" />
                    {Object.entries(normalized.summary.severity || {}).map(([sev, count]) => (
                      <Chip key={sev} label={`${sev}: ${count}`} size="small" />
                    ))}
                  </Stack>
                </Stack>
              </Paper>
            )}

            {normalized && !raw && findings.length > 0 && (
              <Paper variant="outlined" sx={{ p: 2.5, borderRadius: 3 }}>
                <Stack spacing={2}>
                  <Stack direction={{ xs: "column", md: "row" }} spacing={2} alignItems={{ xs: "stretch", md: "center" }}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
                      Findings
                    </Typography>
                    <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                      {["ALL", "CRITICAL", "HIGH", "MEDIUM", "LOW", "UNKNOWN"].map((sev) => (
                        <Chip
                          key={sev}
                          label={sev}
                          size="small"
                          clickable
                          color={severityFilter === sev ? "primary" : "default"}
                          onClick={() => setSeverityFilter(sev)}
                          variant={severityFilter === sev ? "filled" : "outlined"}
                        />
                      ))}
                    </Stack>
                    <TextField
                      value={textFilter}
                      onChange={(event) => setTextFilter(event.target.value)}
                      size="small"
                      placeholder="Filter by CVE, package, version…"
                      sx={{ minWidth: { xs: "100%", md: 260 } }}
                    />
                  </Stack>
                  <Box sx={{ overflowX: "auto" }}>
                    <Table size="small" sx={{ minWidth: 900 }}>
                      <TableHead>
                        <TableRow>
                          <TableCell sx={{ fontWeight: 700 }}>Severity</TableCell>
                          <TableCell sx={{ fontWeight: 700 }}>CVE</TableCell>
                          <TableCell sx={{ fontWeight: 700 }}>Package</TableCell>
                          <TableCell sx={{ fontWeight: 700 }}>Installed</TableCell>
                          <TableCell sx={{ fontWeight: 700 }}>Fixed</TableCell>
                          <TableCell sx={{ fontWeight: 700 }}>Target</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {filteredFindings.map((finding) => (
                          <TableRow key={`${finding.cveId}-${finding.packageName}-${finding.installedVersion}`}>
                            <TableCell>
                              <Chip label={finding.severity || "UNKNOWN"} size="small" />
                            </TableCell>
                            <TableCell sx={{ whiteSpace: "nowrap", fontWeight: 650 }}>
                              {finding.cveId || "—"}
                            </TableCell>
                            <TableCell>{finding.packageName || "—"}</TableCell>
                            <TableCell>{finding.installedVersion || "—"}</TableCell>
                            <TableCell>{finding.fixedVersion || "—"}</TableCell>
                            <TableCell>{finding.sourceTarget || "—"}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </Box>
                  <Typography variant="caption" color="text.secondary">
                    Showing {filteredFindings.length} of {findings.length} findings.
                  </Typography>
                </Stack>
              </Paper>
            )}

            {result !== null && (
              <Box>
                <Typography variant="subtitle1" sx={{ fontWeight: 750, mb: 1 }}>
                  Raw response payload
                </Typography>
                <JsonPanel title="Scan payload" data={result} />
              </Box>
            )}
          </Stack>
        </Paper>
      )}
    </Stack>
  );
}
