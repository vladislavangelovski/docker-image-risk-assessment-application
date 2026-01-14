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
  TextField,
  Typography
} from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import ReceiptLongIcon from "@mui/icons-material/ReceiptLong";
import { api } from "../api/client";
import type { ScanJobStatus, ScanResult } from "../api/types";
import { JsonPanel } from "../components/JsonPanel";

export function ScanViewer() {
  const [scanId, setScanId] = React.useState("");
  const [imageRef, setImageRef] = React.useState("");
  const [raw, setRaw] = React.useState(false);
  const [result, setResult] = React.useState<ScanResult | unknown | null>(null);
  const [jobStatus, setJobStatus] = React.useState<ScanJobStatus | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  const resetErrors = () => {
    setError(null);
  };

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
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to fetch job status.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Stack spacing={3}>
      <Paper className="section-card stagger-in" style={{ "--delay": 0 } as React.CSSProperties}>
        <Stack spacing={2} sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <ReceiptLongIcon sx={{ color: "var(--mint-500)" }} />
            <Typography variant="h5">Scan Viewer</Typography>
          </Stack>
          <Typography color="text.secondary">
            Inspect normalized scan results or raw Trivy output by scan ID or image reference.
          </Typography>
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
        <Paper className="section-card stagger-in" style={{ "--delay": 1 } as React.CSSProperties}>
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
            {result !== null && (
              <Box>
                <Typography variant="subtitle1">Response payload</Typography>
                <JsonPanel data={result} />
              </Box>
            )}
          </Stack>
        </Paper>
      )}
    </Stack>
  );
}
