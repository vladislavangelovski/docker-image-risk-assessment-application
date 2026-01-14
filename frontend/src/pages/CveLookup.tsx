import React from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  Divider,
  Grid,
  Link,
  Paper,
  Stack,
  TextField,
  Typography
} from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import ArticleIcon from "@mui/icons-material/Article";
import { api } from "../api/client";
import type { CveEntry, EpssScore, Page } from "../api/types";
import { JsonPanel } from "../components/JsonPanel";

export function CveLookup() {
  const [cveId, setCveId] = React.useState("");
  const [lookupResult, setLookupResult] = React.useState<CveEntry | null>(null);
  const [epssScores, setEpssScores] = React.useState<EpssScore[]>([]);
  const [lookupError, setLookupError] = React.useState<string | null>(null);
  const [lookupLoading, setLookupLoading] = React.useState(false);
  const [showRaw, setShowRaw] = React.useState(false);

  const [pageData, setPageData] = React.useState<Page<CveEntry> | null>(null);
  const [pageError, setPageError] = React.useState<string | null>(null);
  const [pageLoading, setPageLoading] = React.useState(false);
  const [pageIndex, setPageIndex] = React.useState(0);
  const [pageSize, setPageSize] = React.useState("10");

  const loadPage = React.useCallback(async () => {
    setPageLoading(true);
    setPageError(null);
    try {
      const sizeParsed = Number(pageSize);
      const normalizedSize =
        Number.isFinite(sizeParsed) && sizeParsed > 0 ? sizeParsed : 10;
      const data = await api.cveList(pageIndex, normalizedSize);
      setPageData(data);
    } catch (err) {
      setPageError(err instanceof Error ? err.message : "Failed to load CVE list");
    } finally {
      setPageLoading(false);
    }
  }, [pageIndex, pageSize]);

  React.useEffect(() => {
    loadPage();
  }, [loadPage]);

  const handleLookup = async () => {
    if (!cveId.trim()) {
      setLookupError("CVE id is required.");
      return;
    }
    setLookupError(null);
    setLookupLoading(true);
    try {
      const id = cveId.trim();
      const cve = await api.cveById(id);
      const epss = await api.cveEpss(id, 5);
      setLookupResult(cve);
      setEpssScores(epss);
    } catch (err) {
      setLookupError(err instanceof Error ? err.message : "Lookup failed");
      setLookupResult(null);
      setEpssScores([]);
    } finally {
      setLookupLoading(false);
    }
  };

  return (
    <Stack spacing={3}>
      <Paper className="section-card stagger-in" style={{ "--delay": 0 } as React.CSSProperties}>
        <Stack spacing={2} sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <SearchIcon sx={{ color: "var(--mint-500)" }} />
            <Typography variant="h5">CVE Lookup</Typography>
          </Stack>
          <Typography color="text.secondary">
            Fetch CVE metadata, CVSS vectors, and the latest EPSS scores directly from the
            CVE store.
          </Typography>
          <Grid container spacing={2} alignItems="center">
            <Grid item xs={12} md={6}>
              <TextField
                fullWidth
                label="CVE ID"
                placeholder="CVE-2021-44228"
                value={cveId}
                onChange={(event) => setCveId(event.target.value)}
              />
            </Grid>
            <Grid item xs={12} md={3}>
              <Button variant="contained" onClick={handleLookup} disabled={lookupLoading}>
                {lookupLoading ? "Searching..." : "Fetch CVE"}
              </Button>
            </Grid>
            <Grid item xs={12} md={3}>
              <Button variant="outlined" size="small" onClick={() => setShowRaw(!showRaw)}>
                {showRaw ? "Hide raw JSON" : "Show raw JSON"}
              </Button>
            </Grid>
          </Grid>
          {lookupError && <Alert severity="error">{lookupError}</Alert>}
        </Stack>
      </Paper>

      {lookupResult && (
        <Paper className="section-card stagger-in" style={{ "--delay": 1 } as React.CSSProperties}>
          <Stack spacing={2} sx={{ p: 3 }}>
            <Stack direction="row" spacing={1} alignItems="center">
              <ArticleIcon sx={{ color: "var(--amber-500)" }} />
              <Typography variant="h6">{lookupResult.cveId}</Typography>
              {lookupResult.cvssSeverity && (
                <Chip label={lookupResult.cvssSeverity} color="warning" variant="outlined" />
              )}
              {lookupResult.epssScore !== undefined && (
                <Chip label={`EPSS ${lookupResult.epssScore}`} variant="outlined" />
              )}
            </Stack>
            <Typography color="text.secondary">{lookupResult.description}</Typography>
            <Grid container spacing={2}>
              <Grid item xs={12} md={6}>
                <Stack spacing={1}>
                  <Typography variant="subtitle2">CVSS</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {lookupResult.cvssVector || "No vector available"}
                  </Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    {lookupResult.cvssBaseScore !== undefined && (
                      <Chip label={`Base ${lookupResult.cvssBaseScore}`} size="small" />
                    )}
                    {lookupResult.cvssAttackVector && (
                      <Chip label={lookupResult.cvssAttackVector} size="small" variant="outlined" />
                    )}
                    {lookupResult.cvssPrivilegesRequired && (
                      <Chip label={lookupResult.cvssPrivilegesRequired} size="small" variant="outlined" />
                    )}
                  </Stack>
                </Stack>
              </Grid>
              <Grid item xs={12} md={6}>
                <Stack spacing={1}>
                  <Typography variant="subtitle2">Metadata</Typography>
                  <Typography variant="body2" color="text.secondary">
                    Published: {lookupResult.publishedDate || "n/a"}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Modified: {lookupResult.lastModified || "n/a"}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Status: {lookupResult.vulnStatus || "n/a"}
                  </Typography>
                </Stack>
              </Grid>
            </Grid>
            {(lookupResult.weaknesses || []).length > 0 && (
              <Stack spacing={1}>
                <Typography variant="subtitle2">Weaknesses</Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap">
                  {(lookupResult.weaknesses || []).map((weakness) => (
                    <Chip key={weakness} label={weakness} size="small" variant="outlined" />
                  ))}
                </Stack>
              </Stack>
            )}
            {(lookupResult.references || []).length > 0 && (
              <Stack spacing={1}>
                <Typography variant="subtitle2">References</Typography>
                <Stack spacing={1}>
                  {(lookupResult.references || []).map((ref, index) => (
                    <Link
                      key={`${ref.url}-${index}`}
                      href={ref.url}
                      target="_blank"
                      rel="noreferrer"
                      underline="hover"
                      color="inherit"
                    >
                      {ref.url}
                    </Link>
                  ))}
                </Stack>
              </Stack>
            )}
            <Divider />
            <Stack spacing={1}>
              <Typography variant="subtitle2">Latest EPSS scores</Typography>
              <Stack spacing={1}>
                {epssScores.map((score) => (
                  <Paper key={score.id} sx={{ p: 2, borderRadius: 3 }}>
                    <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
                      <Typography variant="body2">Score: {score.score}</Typography>
                      <Typography variant="body2">Percentile: {score.percentile}</Typography>
                      <Typography variant="body2">Retrieved: {score.retrievedAt}</Typography>
                    </Stack>
                  </Paper>
                ))}
                {epssScores.length === 0 && (
                  <Typography variant="body2" color="text.secondary">
                    No EPSS scores available.
                  </Typography>
                )}
              </Stack>
            </Stack>
            {showRaw && <JsonPanel data={{ cve: lookupResult, epss: epssScores }} />}
          </Stack>
        </Paper>
      )}

      <Paper className="section-card stagger-in" style={{ "--delay": 2 } as React.CSSProperties}>
        <Stack spacing={2} sx={{ p: 3 }}>
          <Typography variant="h6">Browse CVE entries</Typography>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2} alignItems="center">
            <TextField
              label="Page size"
              type="number"
              value={pageSize}
              onChange={(event) => setPageSize(event.target.value)}
              sx={{ width: 140 }}
            />
            <Button variant="outlined" onClick={loadPage} disabled={pageLoading}>
              {pageLoading ? "Refreshing..." : "Refresh"}
            </Button>
            <Stack direction="row" spacing={1} alignItems="center">
              <Button
                variant="outlined"
                disabled={pageIndex === 0}
                onClick={() => setPageIndex((prev) => Math.max(prev - 1, 0))}
              >
                Prev
              </Button>
              <Button
                variant="outlined"
                disabled={pageData ? pageIndex >= pageData.totalPages - 1 : true}
                onClick={() => setPageIndex((prev) => prev + 1)}
              >
                Next
              </Button>
              {pageData && (
                <Typography variant="caption" color="text.secondary">
                  Page {pageData.number + 1} of {pageData.totalPages}
                </Typography>
              )}
            </Stack>
          </Stack>
          {pageError && <Alert severity="error">{pageError}</Alert>}
          <Stack spacing={1}>
            {(pageData?.content || []).map((entry) => (
              <Paper key={entry.cveId} sx={{ p: 2, borderRadius: 3 }}>
                <Stack spacing={1}>
                  <Typography variant="subtitle2">{entry.cveId}</Typography>
                  <Typography variant="body2" color="text.secondary" noWrap>
                    {entry.description}
                  </Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    {entry.cvssBaseScore !== undefined && (
                      <Chip label={`CVSS ${entry.cvssBaseScore}`} size="small" />
                    )}
                    {entry.epssScore !== undefined && (
                      <Chip label={`EPSS ${entry.epssScore}`} size="small" variant="outlined" />
                    )}
                  </Stack>
                </Stack>
              </Paper>
            ))}
            {pageData && pageData.content.length === 0 && (
              <Typography variant="body2" color="text.secondary">
                No entries in this page.
              </Typography>
            )}
          </Stack>
        </Stack>
      </Paper>
    </Stack>
  );
}
