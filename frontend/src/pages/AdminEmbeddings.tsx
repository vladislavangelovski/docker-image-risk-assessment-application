import React from "react";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Grid,
  Paper,
  Stack,
  TextField,
  Typography
} from "@mui/material";
import MemoryIcon from "@mui/icons-material/Memory";
import SearchIcon from "@mui/icons-material/Search";
import RocketLaunchIcon from "@mui/icons-material/RocketLaunch";
import { api } from "../api/client";
import type {
  EmbeddingsIndexResponse,
  EmbeddingsSearchResponse
} from "../api/types";
import { PageHeader } from "../components/PageHeader";
import { JsonPanel } from "../components/JsonPanel";
import { useRecentActivity } from "../hooks/useRecentActivity";

export function AdminEmbeddings() {
  const [cveIds, setCveIds] = React.useState("");
  const [query, setQuery] = React.useState("");
  const [kValue, setKValue] = React.useState("5");
  const [indexResult, setIndexResult] = React.useState<EmbeddingsIndexResponse | null>(null);
  const [searchResult, setSearchResult] = React.useState<EmbeddingsSearchResponse | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const { addActivity } = useRecentActivity();

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
      addActivity({
        kind: "EMBEDDINGS_INDEX",
        label: "Embeddings index",
        description: ids.length ? `${ids.length} CVEs` : "Auto selection",
        href: "/admin/embeddings"
      });
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
      addActivity({
        kind: "EMBEDDINGS_SEARCH",
        label: "Embeddings search",
        description: query.trim(),
        href: "/admin/embeddings"
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Search request failed.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Stack spacing={3}>
      <PageHeader
        title="Embeddings Admin"
        subtitle="Index CVE embeddings and validate semantic retrieval quality."
        icon={<MemoryIcon sx={{ color: "var(--mint-500)" }} />}
      />

      <Alert severity="warning">
        Admin tools are intended for controlled environments. Avoid exposing these routes
        without authentication in production.
      </Alert>

      <Paper className="section-card">
        <Stack spacing={2} sx={{ p: 3 }}>
          <Typography variant="h6" sx={{ fontWeight: 750 }}>
            Index embeddings
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
              <Button
                variant="contained"
                onClick={handleIndex}
                disabled={loading}
                startIcon={<RocketLaunchIcon />}
                endIcon={loading ? <CircularProgress size={18} color="inherit" /> : undefined}
              >
                {loading ? "Indexing…" : "Run index job"}
              </Button>
              {indexResult && (
                <Box sx={{ mt: 2 }}>
                  <Typography variant="body2" color="text.secondary">
                    Indexed: {indexResult.upserted} / Requested: {indexResult.requested}
                  </Typography>
                  <Box sx={{ mt: 1 }}>
                    <JsonPanel title="Index response" data={indexResult} />
                  </Box>
                </Box>
              )}
            </Grid>
          </Grid>
        </Stack>
      </Paper>

      <Paper className="section-card">
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
              <Button
                variant="outlined"
                onClick={handleSearch}
                disabled={loading}
                endIcon={loading ? <CircularProgress size={18} /> : undefined}
              >
                {loading ? "Searching…" : "Run search"}
              </Button>
            </Grid>
          </Grid>
          {searchResult && (
            <Box>
              <Typography variant="subtitle1">Matches</Typography>
              <JsonPanel title="Search response" data={searchResult} />
            </Box>
          )}
          {error && <Alert severity="error">{error}</Alert>}
        </Stack>
      </Paper>
    </Stack>
  );
}
