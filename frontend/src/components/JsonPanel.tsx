import React from "react";
import { Box, IconButton, Paper, Stack, Tooltip, Typography } from "@mui/material";
import ContentCopyRoundedIcon from "@mui/icons-material/ContentCopyRounded";
import DownloadRoundedIcon from "@mui/icons-material/DownloadRounded";
import { copyText } from "../utils/clipboard";

interface JsonPanelProps {
  data: unknown;
  title?: string;
}

function downloadJson(filename: string, data: string) {
  const blob = new Blob([data], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

export function JsonPanel({ data, title = "Raw JSON" }: JsonPanelProps) {
  const [copied, setCopied] = React.useState(false);

  if (data === undefined || data === null) {
    return null;
  }

  const formatted = JSON.stringify(data, null, 2);

  const handleCopy = async () => {
    const ok = await copyText(formatted);
    if (ok) {
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1200);
    }
  };

  const handleDownload = () => {
    const safe = title.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
    downloadJson(`${safe || "payload"}.json`, formatted);
  };

  return (
    <Paper className="surface-card" sx={{ overflow: "hidden" }}>
      <Stack
        direction="row"
        spacing={1}
        alignItems="center"
        justifyContent="space-between"
        sx={{ px: 2, py: 1.5 }}
      >
        <Stack spacing={0.2}>
          <Typography className="kicker" sx={{ fontSize: "0.64rem" }}>
            Audit payload
          </Typography>
          <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
            {title}
          </Typography>
        </Stack>
        <Stack direction="row" spacing={0.5} alignItems="center">
          <Tooltip title={copied ? "Copied" : "Copy JSON"}>
            <span>
              <IconButton size="small" onClick={handleCopy} aria-label="Copy JSON">
                <ContentCopyRoundedIcon fontSize="small" />
              </IconButton>
            </span>
          </Tooltip>
          <Tooltip title="Download JSON">
            <span>
              <IconButton size="small" onClick={handleDownload} aria-label="Download JSON">
                <DownloadRoundedIcon fontSize="small" />
              </IconButton>
            </span>
          </Tooltip>
        </Stack>
      </Stack>
      <Box component="pre" className="json-block" sx={{ m: 0 }}>
        {formatted}
      </Box>
    </Paper>
  );
}
