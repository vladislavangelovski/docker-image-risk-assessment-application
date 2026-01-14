import React from "react";
import {
  Alert,
  Box,
  Button,
  Grid,
  Paper,
  Stack,
  TextField,
  Typography
} from "@mui/material";
import MemoryIcon from "@mui/icons-material/Memory";
import SearchIcon from "@mui/icons-material/Search";
import { api } from "../api/client";
import type {
  EmbeddingsIndexResponse,
  EmbeddingsSearchResponse
} from "../api/types";
import { JsonPanel } from "../components/JsonPanel";

export function AdminEmbeddings() {
  const [cveIds, setCveIds] = React.useState("");
  const [query, setQuery] = React.useState("");
  const [kValue, setKValue] = React.useState("5");
  const [indexResult, setIndexResult] = React.useState<EmbeddingsIndexResponse | null>(null);
  const [searchResult, setSearchResult] = React.useState<EmbeddingsSearchResponse | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  const parseIds = () =>
    cveIds
      .split(/[,\n]/)
      .map((id) => id.trim())
      .filter(Boolean);

  const handleIndex = async () => {
    setError(null);
    setLoading(true);
    try {
      const ids = parseIds();
      const payload = ids.length ? { cveIds: ids } : {};
      const data = await api.embeddingsIndex(payload);
      setIndexResult(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Index request failed.");
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = async () => {
    if (!query.trim()) {
      setError("Search query is required.");
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const trimmedK = kValue.trim();
      const kParsed = Number(trimmedK);
      const k = trimmedK && Number.isFinite(kParsed) ? kParsed : 5;
      const data = await api.embeddingsSearch(query.trim(), k);
      setSearchResult(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Search request failed.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Stack spacing={3}>
      <Paper className="section-card stagger-in" style={{ "--delay": 0 } as React.CSSProperties}>
        <Stack spacing={2} sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <MemoryIcon sx={{ color: "var(--mint-500)" }} />
            <Typography variant="h5">Embeddings Admin</Typography>
          </Stack>
          <Typography color="text.secondary">
            Manage embedding indexing batches and perform semantic search checks.
          </Typography>
          <Grid container spacing={2}>
            <Grid item xs={12} md={8}>
              <TextField
                fullWidth
                label="CVE IDs (comma or newline separated)"
                placeholder="CVE-2024-1234, CVE-2025-5678"
                multiline
                minRows={3}
                value={cveIds}
                onChange={(event) => setCveIds(event.target.value)}
              />
            </Grid>
            <Grid item xs={12} md={4}>
              <Button variant="contained" onClick={handleIndex} disabled={loading}>
                {loading ? "Indexing..." : "Run index job"}
              </Button>
              {indexResult && (
                <Box sx={{ mt: 2 }}>
                  <Typography variant="body2" color="text.secondary">
                    Indexed: {indexResult.upserted} / Requested: {indexResult.requested}
                  </Typography>
                </Box>
              )}
            </Grid>
          </Grid>
        </Stack>
      </Paper>

      <Paper className="section-card stagger-in" style={{ "--delay": 1 } as React.CSSProperties}>
        <Stack spacing={2} sx={{ p: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <SearchIcon sx={{ color: "var(--amber-500)" }} />
            <Typography variant="h6">Semantic search</Typography>
          </Stack>
          <Grid container spacing={2} alignItems="center">
            <Grid item xs={12} md={6}>
              <TextField
                fullWidth
                label="Query"
                placeholder="openssl critical vulnerabilities"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
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
              <Button variant="outlined" onClick={handleSearch} disabled={loading}>
                Run search
              </Button>
            </Grid>
          </Grid>
          {searchResult && (
            <Box>
              <Typography variant="subtitle1">Matches</Typography>
              <JsonPanel data={searchResult} />
            </Box>
          )}
          {error && <Alert severity="error">{error}</Alert>}
        </Stack>
      </Paper>
    </Stack>
  );
}
