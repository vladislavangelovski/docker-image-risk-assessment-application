import React from "react";
import {
  Box,
  Button,
  Chip,
  Grid,
  Paper,
  Stack,
  Typography
} from "@mui/material";
import { Link } from "react-router-dom";
import RocketLaunchIcon from "@mui/icons-material/RocketLaunch";
import SearchIcon from "@mui/icons-material/Search";
import PsychologyIcon from "@mui/icons-material/Psychology";
import ShieldOutlinedIcon from "@mui/icons-material/ShieldOutlined";

export function Dashboard() {
  const highlights = [
    "Gateway-first API access",
    "Trivy-backed image scanning",
    "CVE + EPSS enrichment",
    "RAG summaries with citations"
  ];

  return (
    <Stack spacing={4}>
      <Paper className="hero-card stagger-in" style={{ "--delay": 0 } as React.CSSProperties}>
        <Stack spacing={2}>
          <Typography variant="h3">Modern risk intelligence for container images.</Typography>
          <Typography variant="subtitle1" color="text.secondary">
            Investigate vulnerable packages, understand exploitability signals, and ask
            semantic questions without leaving the gateway.
          </Typography>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <Button
              variant="contained"
              size="large"
              startIcon={<RocketLaunchIcon />}
              component={Link}
              to="/assess"
            >
              Assess an Image
            </Button>
            <Button
              variant="outlined"
              size="large"
              startIcon={<PsychologyIcon />}
              component={Link}
              to="/qa"
            >
              Ask a Question
            </Button>
            <Button
              variant="outlined"
              size="large"
              startIcon={<SearchIcon />}
              component={Link}
              to="/cves"
            >
              Lookup a CVE
            </Button>
          </Stack>
          <Stack direction="row" spacing={1} flexWrap="wrap">
            {highlights.map((item) => (
              <Chip key={item} label={item} variant="outlined" />
            ))}
          </Stack>
        </Stack>
      </Paper>

      <Grid container spacing={3}>
        <Grid item xs={12} md={6}>
          <Paper
            className="section-card stagger-in"
            style={{ "--delay": 1 } as React.CSSProperties}
            sx={{ p: 3 }}
          >
            <Stack spacing={2}>
              <Stack direction="row" spacing={1} alignItems="center">
                <ShieldOutlinedIcon sx={{ color: "var(--mint-500)" }} />
                <Typography variant="h6">Core workflow</Typography>
              </Stack>
              <Typography color="text.secondary">
                Scan images, enrich with CVE + EPSS intel, then ask direct questions on the
                evidence returned by the AI service.
              </Typography>
              <Stack spacing={1}>
                {[
                  "1. Submit image reference",
                  "2. Normalize scan findings",
                  "3. Enrich with CVE + EPSS",
                  "4. Score risk + cite evidence"
                ].map((step) => (
                  <Typography key={step} variant="body2">
                    {step}
                  </Typography>
                ))}
              </Stack>
            </Stack>
          </Paper>
        </Grid>
        <Grid item xs={12} md={6}>
          <Paper
            className="section-card stagger-in"
            style={{ "--delay": 2 } as React.CSSProperties}
            sx={{ p: 3 }}
          >
            <Stack spacing={2}>
              <Typography variant="h6">Operational reminders</Typography>
              <Typography color="text.secondary">
                Keep requests scoped to the gateway endpoint only. The UI never calls
                services directly.
              </Typography>
              <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                <Chip label="Gateway-only" color="success" />
                <Chip label="Raw JSON available" variant="outlined" />
              </Stack>
              <Typography variant="body2" color="text.secondary">
                Tip: provide an image reference with QA prompts to auto-index missing
                embeddings for faster semantic answers.
              </Typography>
            </Stack>
          </Paper>
        </Grid>
      </Grid>

      <Paper
        className="section-card stagger-in"
        style={{ "--delay": 3 } as React.CSSProperties}
        sx={{ p: 3 }}
      >
        <Stack spacing={2}>
          <Typography variant="h6">Suggested test prompts</Typography>
          <Typography color="text.secondary">
            Try these after your stack is running to validate the end-to-end flow.
          </Typography>
          <Box
            sx={{
              display: "grid",
              gridTemplateColumns: { xs: "1fr", md: "repeat(3, 1fr)" },
              gap: 2
            }}
          >
            <Paper sx={{ p: 2, borderRadius: 3 }}>
              <Typography variant="subtitle2">Assess</Typography>
              <Typography variant="body2" color="text.secondary">
                imageRef: <strong>nginx:1.25</strong>
              </Typography>
            </Paper>
            <Paper sx={{ p: 2, borderRadius: 3 }}>
              <Typography variant="subtitle2">Question</Typography>
              <Typography variant="body2" color="text.secondary">
                "Which findings are most exploitable in this image?"
              </Typography>
            </Paper>
            <Paper sx={{ p: 2, borderRadius: 3 }}>
              <Typography variant="subtitle2">CVE</Typography>
              <Typography variant="body2" color="text.secondary">
                CVE-2021-44228
              </Typography>
            </Paper>
          </Box>
        </Stack>
      </Paper>
    </Stack>
  );
}
