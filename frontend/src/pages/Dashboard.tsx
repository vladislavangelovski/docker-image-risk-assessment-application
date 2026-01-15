import React from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  Grid,
  Paper,
  Skeleton,
  Stack,
  Typography
} from "@mui/material";
import { Link } from "react-router-dom";
import RocketLaunchIcon from "@mui/icons-material/RocketLaunch";
import SearchIcon from "@mui/icons-material/Search";
import PsychologyIcon from "@mui/icons-material/Psychology";
import DashboardRoundedIcon from "@mui/icons-material/DashboardRounded";
import ShieldRoundedIcon from "@mui/icons-material/ShieldRounded";
import FactCheckRoundedIcon from "@mui/icons-material/FactCheckRounded";
import BugReportRoundedIcon from "@mui/icons-material/BugReportRounded";
import ReceiptLongRoundedIcon from "@mui/icons-material/ReceiptLongRounded";
import MemoryRoundedIcon from "@mui/icons-material/MemoryRounded";
import { PageHeader } from "../components/PageHeader";
import { api } from "../api/client";
import type { Page as PageResponse, CveEntry } from "../api/types";
import { useRecentActivity } from "../hooks/useRecentActivity";
import { formatRelativeTime } from "../utils/time";

export function Dashboard() {
  const { items, clearActivity } = useRecentActivity();

  const [cveSummary, setCveSummary] = React.useState<PageResponse<CveEntry> | null>(null);
  const [cveError, setCveError] = React.useState<string | null>(null);
  const [cveLoading, setCveLoading] = React.useState(true);

  React.useEffect(() => {
    let cancelled = false;

    (async () => {
      setCveLoading(true);
      setCveError(null);

      const cveResult = await Promise.allSettled([api.cveList(0, 1)]);

      if (cancelled) return;

      if (cveResult[0].status === "fulfilled") {
        setCveSummary(cveResult[0].value);
      } else {
        setCveError(
          cveResult[0].reason instanceof Error
            ? cveResult[0].reason.message
            : "Failed to load CVE stats"
        );
      }
      setCveLoading(false);
    })();

    return () => {
      cancelled = true;
    };
  }, []);

  const activityIcon = (kind: string) => {
    switch (kind) {
      case "ASSESS_IMAGE":
        return <ShieldRoundedIcon />;
      case "QA_QUESTION":
        return <PsychologyIcon />;
      case "QA_CLAIM":
        return <FactCheckRoundedIcon />;
      case "CVE_LOOKUP":
        return <BugReportRoundedIcon />;
      case "SCAN_VIEW":
        return <ReceiptLongRoundedIcon />;
      default:
        return <MemoryRoundedIcon />;
    }
  };

  return (
    <Stack spacing={4}>
      <PageHeader
        title="Dashboard"
        subtitle="Demo shortcuts, inventory stats, and recently viewed items."
        icon={<DashboardRoundedIcon sx={{ color: "var(--mint-500)" }} />}
        actions={
          <Stack direction={{ xs: "column", sm: "row" }} spacing={1} alignItems={{ xs: "stretch", sm: "center" }}>
            <Button
              variant="contained"
              startIcon={<RocketLaunchIcon />}
              component={Link}
              to="/assess"
            >
              New assessment
            </Button>
            <Button variant="outlined" startIcon={<PsychologyIcon />} component={Link} to="/qa">
              New question
            </Button>
          </Stack>
        }
      />

      <Grid container spacing={3}>
        <Grid item xs={12} md={6} lg={6}>
          <Paper className="section-card" sx={{ p: 3 }}>
            <Stack spacing={1.5}>
              <Stack direction="row" spacing={1} alignItems="center">
                <BugReportRoundedIcon sx={{ color: "var(--amber-500)" }} />
                <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                  CVE inventory
                </Typography>
              </Stack>
              {cveLoading ? (
                <Stack spacing={1}>
                  <Skeleton height={44} width="70%" />
                  <Skeleton height={18} width="85%" />
                </Stack>
              ) : cveError ? (
                <Alert severity="warning" sx={{ borderRadius: 3 }}>
                  {cveError}
                </Alert>
              ) : (
                <Stack spacing={1}>
                  <Typography variant="h4" sx={{ fontWeight: 800 }}>
                    {cveSummary?.totalElements?.toLocaleString() ?? "—"}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Total CVE entries currently available for lookups and enrichment.
                  </Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Chip label={`${cveSummary?.totalPages ?? "—"} pages`} size="small" variant="outlined" />
                    <Chip label={`Page size: ${cveSummary?.size ?? "—"}`} size="small" variant="outlined" />
                  </Stack>
                </Stack>
              )}
            </Stack>
          </Paper>
        </Grid>

        <Grid item xs={12} md={6} lg={6}>
          <Paper className="section-card" sx={{ p: 3 }}>
            <Stack spacing={1.5}>
              <Stack direction="row" spacing={1} alignItems="center">
                <ReceiptLongRoundedIcon sx={{ color: "var(--mint-500)" }} />
                <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                  Demo shortcuts
                </Typography>
              </Stack>
              <Typography variant="body2" color="text.secondary">
                Pre-filled routes for quick walk-throughs and repeatable demos.
              </Typography>
              <Stack spacing={1}>
                <Button
                  variant="outlined"
                  startIcon={<ShieldRoundedIcon />}
                  component={Link}
                  to="/assess?imageRef=nginx%3A1.25&k=6"
                >
                  Assess nginx:1.25
                </Button>
                <Button
                  variant="outlined"
                  startIcon={<PsychologyIcon />}
                  component={Link}
                  to="/qa?tab=question&imageRef=nginx%3A1.25&k=4"
                >
                  Ask: most exploitable findings
                </Button>
                <Button
                  variant="outlined"
                  startIcon={<SearchIcon />}
                  component={Link}
                  to="/cves?cveId=CVE-2021-44228"
                >
                  Lookup CVE-2021-44228
                </Button>
              </Stack>
            </Stack>
          </Paper>
        </Grid>
      </Grid>

      <Grid container spacing={3}>
        <Grid item xs={12} md={6}>
          <Paper className="hero-card" sx={{ p: 3 }}>
            <Stack spacing={1.5}>
              <Typography variant="h5" sx={{ fontWeight: 750 }}>
                Explore risk with confidence.
              </Typography>
              <Typography color="text.secondary">
                Assess images, inspect scans, and ask evidence-backed questions. Every screen
                keeps an audit trail with raw JSON payloads for verification.
              </Typography>
              <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5}>
                <Button variant="contained" startIcon={<RocketLaunchIcon />} component={Link} to="/assess">
                  Assess an image
                </Button>
                <Button variant="outlined" startIcon={<SearchIcon />} component={Link} to="/cves">
                  Browse CVEs
                </Button>
              </Stack>
              <Stack direction="row" spacing={1} flexWrap="wrap">
                {["Risk bands", "CVE + EPSS enrichment", "Citations", "Raw JSON audit"].map((item) => (
                  <Chip key={item} label={item} variant="outlined" size="small" />
                ))}
              </Stack>
            </Stack>
          </Paper>
        </Grid>
        <Grid item xs={12} md={6}>
          <Paper className="section-card" sx={{ p: 3 }}>
            <Stack spacing={1.5}>
              <Typography variant="h6" sx={{ fontWeight: 750 }}>
                Recent activity
              </Typography>
              {items.length === 0 ? (
                <Typography color="text.secondary" variant="body2">
                  No recent activity yet. Start with an image assessment or a CVE lookup.
                </Typography>
              ) : (
                <Stack spacing={1}>
                  {items.slice(0, 6).map((item) => (
                    <Paper
                      key={item.id}
                      variant="outlined"
                      sx={{
                        p: 1.5,
                        borderRadius: 3,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "space-between",
                        gap: 2
                      }}
                    >
                      <Stack direction="row" spacing={1.5} alignItems="center">
                        <Box
                          sx={{
                            width: 36,
                            height: 36,
                            borderRadius: 3,
                            display: "grid",
                            placeItems: "center",
                            backgroundColor: "rgba(30, 168, 150, 0.12)"
                          }}
                        >
                          {activityIcon(item.kind)}
                        </Box>
                        <Box>
                          <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                            {item.label}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            {item.description ? `${item.description} • ` : ""}
                            {formatRelativeTime(item.timestamp)}
                          </Typography>
                        </Box>
                      </Stack>
                      <Button variant="outlined" size="small" component={Link} to={item.href}>
                        Open
                      </Button>
                    </Paper>
                  ))}
                </Stack>
              )}
              {items.length > 0 && (
                <Box>
                  <Button variant="text" size="small" onClick={clearActivity}>
                    Clear history
                  </Button>
                </Box>
              )}
            </Stack>
          </Paper>
        </Grid>
      </Grid>
    </Stack>
  );
}
