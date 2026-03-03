import React from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Grid,
  Paper,
  Stack,
  TableSortLabel,
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
import RefreshRoundedIcon from "@mui/icons-material/RefreshRounded";
import ReceiptLongIcon from "@mui/icons-material/ReceiptLong";
import TuneRoundedIcon from "@mui/icons-material/TuneRounded";
import { useSearchParams } from "react-router-dom";
import { api } from "../api/client";
import type { Page, ScanHistoryItem, ScanResult, Severity } from "../api/types";
import { PageHeader } from "../components/PageHeader";
import { JsonPanel } from "../components/JsonPanel";
import { useRecentActivity } from "../hooks/useRecentActivity";

const HISTORY_PAGE_SIZE = 50;
const HISTORY_SEVERITY_OPTIONS: Array<"ALL" | Severity> = [
  "ALL",
  "LOW",
  "MEDIUM",
  "HIGH",
  "CRITICAL"
];
type HistorySortColumn =
  | "finishedAt"
  | "image"
  | "maxSeverity"
  | "totalFindings"
  | "fixAvailable"
  | "scannerVersion";
type SortDirection = "asc" | "desc";

function severityRank(value?: Severity): number {
  switch (value) {
    case "CRITICAL":
      return 4;
    case "HIGH":
      return 3;
    case "MEDIUM":
      return 2;
    case "LOW":
      return 1;
    case "UNKNOWN":
      return 0;
    default:
      return -1;
  }
}

function defaultSortDirection(column: HistorySortColumn): SortDirection {
  switch (column) {
    case "image":
    case "scannerVersion":
      return "asc";
    default:
      return "desc";
  }
}

function normalizeText(value?: string): string {
  return (value || "").trim().toLowerCase();
}

function normalizeTimestamp(value?: string): number {
  if (!value) return Number.NEGATIVE_INFINITY;
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : Number.NEGATIVE_INFINITY;
}

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

function parsePage(raw: string | null): number {
  if (!raw) return 0;
  const page = Number(raw);
  if (!Number.isInteger(page) || page < 0) return 0;
  return page;
}

function parseHistorySeverity(raw: string | null): "ALL" | Severity {
  if (!raw) return "ALL";
  const upper = raw.toUpperCase();
  if (HISTORY_SEVERITY_OPTIONS.includes(upper as "ALL" | Severity)) {
    return upper as "ALL" | Severity;
  }
  return "ALL";
}

function formatDate(value?: string) {
  if (!value) return "—";
  const parsed = Date.parse(value);
  if (!Number.isFinite(parsed)) return value;
  return new Date(parsed).toLocaleString();
}

function severityColor(
  value?: Severity
): "default" | "error" | "warning" | "info" | "success" {
  switch (value) {
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

export function ScanViewer() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { addActivity } = useRecentActivity();

  const page = parsePage(searchParams.get("page"));
  const activeImageRef = searchParams.get("imageRef") || "";
  const activeMinSeverity = parseHistorySeverity(searchParams.get("minSeverity"));
  const activeScanId = searchParams.get("scanId") || "";
  const raw =
    searchParams.get("raw") === "true" || searchParams.get("raw") === "1";

  const [imageFilter, setImageFilter] = React.useState(activeImageRef);
  const [minSeverityFilter, setMinSeverityFilter] =
    React.useState<"ALL" | Severity>(activeMinSeverity);

  const [history, setHistory] = React.useState<Page<ScanHistoryItem> | null>(null);
  const [historyLoading, setHistoryLoading] = React.useState(false);
  const [historyError, setHistoryError] = React.useState<string | null>(null);
  const [historyRefreshKey, setHistoryRefreshKey] = React.useState(0);

  const [detail, setDetail] = React.useState<ScanResult | unknown | null>(null);
  const [detailLoading, setDetailLoading] = React.useState(false);
  const [detailError, setDetailError] = React.useState<string | null>(null);

  const [findingSeverityFilter, setFindingSeverityFilter] = React.useState("ALL");
  const [findingTextFilter, setFindingTextFilter] = React.useState("");
  const [historySortBy, setHistorySortBy] =
    React.useState<HistorySortColumn>("finishedAt");
  const [historySortDirection, setHistorySortDirection] =
    React.useState<SortDirection>("desc");

  React.useEffect(() => {
    setImageFilter(activeImageRef);
    setMinSeverityFilter(activeMinSeverity);
  }, [activeImageRef, activeMinSeverity]);

  React.useEffect(() => {
    let cancelled = false;
    setHistoryLoading(true);
    setHistoryError(null);

    (async () => {
      try {
        const data = await api.scanHistory(
          page,
          HISTORY_PAGE_SIZE,
          activeImageRef,
          activeMinSeverity
        );
        if (cancelled) return;
        setHistory(data);
      } catch (err) {
        if (cancelled) return;
        setHistoryError(
          err instanceof Error ? err.message : "Failed to load scan history."
        );
      } finally {
        if (!cancelled) {
          setHistoryLoading(false);
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [page, activeImageRef, activeMinSeverity, historyRefreshKey]);

  React.useEffect(() => {
    if (!activeScanId) {
      setDetail(null);
      setDetailError(null);
      return;
    }

    let cancelled = false;
    setDetailLoading(true);
    setDetailError(null);
    setFindingTextFilter("");
    setFindingSeverityFilter("ALL");

    (async () => {
      try {
        const data = await api.scanById(activeScanId, raw);
        if (cancelled) return;
        setDetail(data);
      } catch (err) {
        if (cancelled) return;
        setDetail(null);
        setDetailError(
          err instanceof Error ? err.message : "Failed to load scan details."
        );
      } finally {
        if (!cancelled) {
          setDetailLoading(false);
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [activeScanId, raw]);

  const applyFilters = () => {
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set("page", "0");
    if (imageFilter.trim()) {
      nextParams.set("imageRef", imageFilter.trim());
    } else {
      nextParams.delete("imageRef");
    }
    if (minSeverityFilter !== "ALL") {
      nextParams.set("minSeverity", minSeverityFilter);
    } else {
      nextParams.delete("minSeverity");
    }
    setSearchParams(nextParams, { replace: true });
  };

  const clearFilters = () => {
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set("page", "0");
    nextParams.delete("imageRef");
    nextParams.delete("minSeverity");
    setSearchParams(nextParams, { replace: true });
  };

  const openScan = (scan: ScanHistoryItem) => {
    if (!scan.scanId) return;
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set("scanId", scan.scanId);
    nextParams.set("raw", raw ? "true" : "false");
    setSearchParams(nextParams, { replace: true });
    addActivity({
      kind: "SCAN_VIEW",
      label: scan.image || `Scan ${scan.scanId}`,
      description: scan.maxSeverity
        ? `Max severity: ${scan.maxSeverity}`
        : "Scan history",
      href: `/scans?scanId=${encodeURIComponent(scan.scanId)}&raw=${
        raw ? "true" : "false"
      }`
    });
  };

  const toggleRawMode = () => {
    if (!activeScanId) {
      return;
    }
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set("scanId", activeScanId);
    nextParams.set("raw", raw ? "false" : "true");
    setSearchParams(nextParams, { replace: true });
  };

  const goToPage = (nextPage: number) => {
    const normalizedNextPage = Math.max(0, nextPage);
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set("page", String(normalizedNextPage));
    setSearchParams(nextParams, { replace: true });
  };

  const normalized = isScanResult(detail) ? detail : null;
  const findings = normalized?.findings || [];
  const filteredFindings = findings.filter((finding) => {
    const severity = (finding.severity || "UNKNOWN").toUpperCase();
    const severityMatch =
      findingSeverityFilter === "ALL" || severity === findingSeverityFilter;
    if (!severityMatch) return false;

    const q = findingTextFilter.trim().toLowerCase();
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

  const historyItems = history?.content || [];
  const sortedHistoryItems = React.useMemo(() => {
    const directionFactor = historySortDirection === "asc" ? 1 : -1;
    return [...historyItems].sort((left, right) => {
      let compare = 0;
      switch (historySortBy) {
        case "finishedAt":
          compare =
            normalizeTimestamp(left.finishedAt) - normalizeTimestamp(right.finishedAt);
          break;
        case "image":
          compare = normalizeText(left.image).localeCompare(normalizeText(right.image));
          break;
        case "maxSeverity":
          compare = severityRank(left.maxSeverity) - severityRank(right.maxSeverity);
          break;
        case "totalFindings":
          compare = (left.totalFindings ?? 0) - (right.totalFindings ?? 0);
          break;
        case "fixAvailable":
          compare = (left.fixAvailable ?? 0) - (right.fixAvailable ?? 0);
          break;
        case "scannerVersion":
          compare = normalizeText(left.scannerVersion).localeCompare(
            normalizeText(right.scannerVersion)
          );
          break;
        default:
          compare = 0;
      }

      if (compare !== 0) {
        return compare * directionFactor;
      }

      const finishedCompare =
        normalizeTimestamp(right.finishedAt) - normalizeTimestamp(left.finishedAt);
      if (finishedCompare !== 0) {
        return finishedCompare;
      }

      return normalizeText(left.scanId).localeCompare(normalizeText(right.scanId));
    });
  }, [historyItems, historySortBy, historySortDirection]);
  const canGoPrev = page > 0;
  const canGoNext = history ? page < history.totalPages - 1 : false;

  const onSort = (column: HistorySortColumn) => {
    if (historySortBy === column) {
      setHistorySortDirection((current) => (current === "asc" ? "desc" : "asc"));
      return;
    }
    setHistorySortBy(column);
    setHistorySortDirection(defaultSortDirection(column));
  };

  return (
    <Stack spacing={3}>
      <PageHeader
        title="Scan History"
        subtitle="Browse recent scans, then open normalized or raw scanner output."
        icon={<ReceiptLongIcon sx={{ color: "var(--mint-500)" }} />}
      />

      <Paper className="hero-card scan-hero-card animate-rise">
        <Stack spacing={2} sx={{ p: { xs: 2.25, sm: 3 } }}>
          <Typography className="kicker">History filters</Typography>
          <Grid container spacing={2} alignItems="center">
            <Grid item xs={12} md={6}>
              <TextField
                fullWidth
                label="Image reference contains"
                placeholder="nginx:1.25"
                value={imageFilter}
                onChange={(event) => setImageFilter(event.target.value)}
              />
            </Grid>
            <Grid item xs={12} md={6}>
              <Stack
                direction="row"
                spacing={1}
                alignItems="center"
                flexWrap="wrap"
                sx={{ rowGap: 1 }}
              >
                {HISTORY_SEVERITY_OPTIONS.map((severity) => (
                  <Chip
                    key={severity}
                    label={severity === "ALL" ? "All severities" : `Min ${severity}`}
                    size="small"
                    clickable
                    color={
                      minSeverityFilter === severity ? "primary" : "default"
                    }
                    onClick={() => setMinSeverityFilter(severity)}
                    variant={
                      minSeverityFilter === severity ? "filled" : "outlined"
                    }
                  />
                ))}
              </Stack>
            </Grid>
          </Grid>

          <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
            <Button
              variant="contained"
              onClick={applyFilters}
              startIcon={<SearchIcon />}
              sx={{ width: { xs: "100%", md: "auto" } }}
            >
              Apply filters
            </Button>
            <Button
              variant="outlined"
              onClick={() =>
                setHistoryRefreshKey((current) => current + 1)
              }
              startIcon={<RefreshRoundedIcon />}
              sx={{ width: { xs: "100%", md: "auto" } }}
            >
              Refresh
            </Button>
            <Button
              variant="text"
              onClick={clearFilters}
              sx={{ width: { xs: "100%", md: "auto" } }}
            >
              Clear filters
            </Button>
          </Stack>
          {historyError && <Alert severity="error">{historyError}</Alert>}
        </Stack>
      </Paper>

      <Paper className="section-card">
        <Stack spacing={2} sx={{ p: { xs: 2.25, sm: 3 } }}>
          <Stack
            direction={{ xs: "column", md: "row" }}
            spacing={1}
            alignItems={{ xs: "flex-start", md: "center" }}
            justifyContent="space-between"
          >
            <Typography variant="h6">Recent scans</Typography>
            <Typography variant="caption" color="text.secondary">
              Showing up to {HISTORY_PAGE_SIZE} scans per page.
            </Typography>
          </Stack>

          {historyLoading ? (
            <Stack
              direction="row"
              spacing={1}
              alignItems="center"
              sx={{ py: 2 }}
            >
              <CircularProgress size={20} />
              <Typography variant="body2" color="text.secondary">
                Loading scan history...
              </Typography>
            </Stack>
          ) : (
            <>
              <Box sx={{ overflowX: "auto" }}>
                <Table className="interactive-table" size="small" sx={{ minWidth: 980 }}>
                  <TableHead>
                    <TableRow>
                      <TableCell
                        sx={{ fontWeight: 700 }}
                        sortDirection={
                          historySortBy === "finishedAt" ? historySortDirection : false
                        }
                      >
                        <TableSortLabel
                          active={historySortBy === "finishedAt"}
                          direction={
                            historySortBy === "finishedAt" ? historySortDirection : "desc"
                          }
                          onClick={() => onSort("finishedAt")}
                        >
                          Finished
                        </TableSortLabel>
                      </TableCell>
                      <TableCell
                        sx={{ fontWeight: 700 }}
                        sortDirection={historySortBy === "image" ? historySortDirection : false}
                      >
                        <TableSortLabel
                          active={historySortBy === "image"}
                          direction={historySortBy === "image" ? historySortDirection : "asc"}
                          onClick={() => onSort("image")}
                        >
                          Image
                        </TableSortLabel>
                      </TableCell>
                      <TableCell
                        sx={{ fontWeight: 700 }}
                        sortDirection={
                          historySortBy === "maxSeverity" ? historySortDirection : false
                        }
                      >
                        <TableSortLabel
                          active={historySortBy === "maxSeverity"}
                          direction={
                            historySortBy === "maxSeverity" ? historySortDirection : "desc"
                          }
                          onClick={() => onSort("maxSeverity")}
                        >
                          Severity
                        </TableSortLabel>
                      </TableCell>
                      <TableCell
                        sx={{ fontWeight: 700 }}
                        align="right"
                        sortDirection={
                          historySortBy === "totalFindings" ? historySortDirection : false
                        }
                      >
                        <TableSortLabel
                          active={historySortBy === "totalFindings"}
                          direction={
                            historySortBy === "totalFindings" ? historySortDirection : "desc"
                          }
                          onClick={() => onSort("totalFindings")}
                        >
                          Findings
                        </TableSortLabel>
                      </TableCell>
                      <TableCell
                        sx={{ fontWeight: 700 }}
                        align="right"
                        sortDirection={
                          historySortBy === "fixAvailable" ? historySortDirection : false
                        }
                      >
                        <TableSortLabel
                          active={historySortBy === "fixAvailable"}
                          direction={
                            historySortBy === "fixAvailable" ? historySortDirection : "desc"
                          }
                          onClick={() => onSort("fixAvailable")}
                        >
                          Fixes
                        </TableSortLabel>
                      </TableCell>
                      <TableCell
                        sx={{ fontWeight: 700 }}
                        sortDirection={
                          historySortBy === "scannerVersion"
                            ? historySortDirection
                            : false
                        }
                      >
                        <TableSortLabel
                          active={historySortBy === "scannerVersion"}
                          direction={
                            historySortBy === "scannerVersion" ? historySortDirection : "asc"
                          }
                          onClick={() => onSort("scannerVersion")}
                        >
                          Scanner
                        </TableSortLabel>
                      </TableCell>
                      <TableCell sx={{ fontWeight: 700 }} align="right">
                        Action
                      </TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {sortedHistoryItems.map((item) => (
                      <TableRow
                        key={item.scanId || `${item.image}-${item.finishedAt}`}
                        hover
                      >
                        <TableCell sx={{ whiteSpace: "nowrap" }}>
                          {formatDate(item.finishedAt)}
                        </TableCell>
                        <TableCell sx={{ maxWidth: 280 }}>
                          <Typography noWrap>{item.image || "—"}</Typography>
                        </TableCell>
                        <TableCell>
                          <Chip
                            label={item.maxSeverity || "UNKNOWN"}
                            size="small"
                            color={severityColor(item.maxSeverity)}
                            variant="outlined"
                          />
                        </TableCell>
                        <TableCell align="right">
                          {item.totalFindings ?? 0}
                        </TableCell>
                        <TableCell align="right">{item.fixAvailable ?? 0}</TableCell>
                        <TableCell sx={{ maxWidth: 180 }}>
                          <Typography noWrap>
                            {item.scannerVersion || "—"}
                          </Typography>
                        </TableCell>
                        <TableCell align="right">
                          <Button
                            variant="outlined"
                            size="small"
                            onClick={() => openScan(item)}
                            disabled={!item.scanId}
                          >
                            Open
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Box>
              {sortedHistoryItems.length === 0 && (
                <Typography variant="body2" color="text.secondary">
                  No scans found for the current filters.
                </Typography>
              )}
              <Stack
                direction={{ xs: "column", sm: "row" }}
                spacing={1}
                alignItems={{ xs: "stretch", sm: "center" }}
                justifyContent="space-between"
              >
                <Typography variant="caption" color="text.secondary">
                  {history
                    ? `Page ${history.number + 1} of ${Math.max(history.totalPages, 1)} • ${history.totalElements} total`
                    : "Page 1 of 1"}
                </Typography>
                <Stack direction="row" spacing={1}>
                  <Button
                    variant="outlined"
                    size="small"
                    onClick={() => goToPage(page - 1)}
                    disabled={!canGoPrev}
                  >
                    Prev
                  </Button>
                  <Button
                    variant="outlined"
                    size="small"
                    onClick={() => goToPage(page + 1)}
                    disabled={!canGoNext}
                  >
                    Next
                  </Button>
                </Stack>
              </Stack>
            </>
          )}
        </Stack>
      </Paper>

      {activeScanId && (
        <Paper className="section-card">
          <Stack spacing={2} sx={{ p: { xs: 2.25, sm: 3 } }}>
            <Stack
              direction={{ xs: "column", md: "row" }}
              spacing={1}
              alignItems={{ xs: "flex-start", md: "center" }}
              justifyContent="space-between"
            >
              <Stack direction="row" spacing={1} alignItems="center">
                <Typography variant="h6">Scan details</Typography>
                <Chip label={activeScanId} size="small" variant="outlined" />
              </Stack>
              <Stack direction="row" spacing={1} alignItems="center">
                <Switch checked={raw} onChange={toggleRawMode} />
                <Typography variant="caption">Raw mode</Typography>
              </Stack>
            </Stack>

            {detailLoading && (
              <Stack
                direction="row"
                spacing={1}
                alignItems="center"
                sx={{ py: 1 }}
              >
                <CircularProgress size={20} />
                <Typography variant="body2" color="text.secondary">
                  Loading scan details...
                </Typography>
              </Stack>
            )}
            {detailError && <Alert severity="error">{detailError}</Alert>}

            {normalized && normalized.summary && (
              <Paper className="surface-card" sx={{ p: 2.5, borderRadius: 3 }}>
                <Stack spacing={1.5}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <TuneRoundedIcon sx={{ color: "var(--amber-500)" }} />
                    <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
                      Summary
                    </Typography>
                  </Stack>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    {normalized.image && (
                      <Chip
                        label={`Image: ${normalized.image}`}
                        variant="outlined"
                        size="small"
                      />
                    )}
                    <Chip
                      label={`Total: ${normalized.summary.total}`}
                      variant="outlined"
                      size="small"
                    />
                    <Chip
                      label={`Fixes: ${normalized.summary.fixAvailable}`}
                      variant="outlined"
                      size="small"
                    />
                    {Object.entries(normalized.summary.severity || {}).map(
                      ([sev, count]) => (
                        <Chip key={sev} label={`${sev}: ${count}`} size="small" />
                      )
                    )}
                  </Stack>
                </Stack>
              </Paper>
            )}

            {normalized && !raw && findings.length > 0 && (
              <Paper className="surface-card" sx={{ p: 2.5, borderRadius: 3 }}>
                <Stack spacing={2}>
                  <Stack
                    direction={{ xs: "column", md: "row" }}
                    spacing={2}
                    alignItems={{ xs: "stretch", md: "center" }}
                  >
                    <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
                      Findings
                    </Typography>
                    <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                      {["ALL", "CRITICAL", "HIGH", "MEDIUM", "LOW", "UNKNOWN"].map(
                        (severity) => (
                          <Chip
                            key={severity}
                            label={severity}
                            size="small"
                            clickable
                            color={
                              findingSeverityFilter === severity
                                ? "primary"
                                : "default"
                            }
                            onClick={() => setFindingSeverityFilter(severity)}
                            variant={
                              findingSeverityFilter === severity
                                ? "filled"
                                : "outlined"
                            }
                          />
                        )
                      )}
                    </Stack>
                    <TextField
                      value={findingTextFilter}
                      onChange={(event) =>
                        setFindingTextFilter(event.target.value)
                      }
                      size="small"
                      placeholder="Filter by CVE, package, version..."
                      sx={{ minWidth: { xs: "100%", md: 260 } }}
                    />
                  </Stack>
                  <Box sx={{ overflowX: "auto" }}>
                    <Table className="interactive-table" size="small" sx={{ minWidth: 900 }}>
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
                          <TableRow
                            key={`${finding.cveId}-${finding.packageName}-${finding.installedVersion}`}
                          >
                            <TableCell>
                              <Chip
                                label={finding.severity || "UNKNOWN"}
                                size="small"
                              />
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

            {detail !== null && (
              <Box>
                <Typography variant="subtitle1" sx={{ fontWeight: 750, mb: 1 }}>
                  Raw response payload
                </Typography>
                <JsonPanel title="Scan payload" data={detail} />
              </Box>
            )}
          </Stack>
        </Paper>
      )}
    </Stack>
  );
}
