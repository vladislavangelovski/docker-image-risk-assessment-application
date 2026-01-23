import React from "react";
import { Alert, Paper, Stack, Typography } from "@mui/material";

export function Forbidden() {
  return (
    <Paper className="section-card">
      <Stack spacing={2} sx={{ p: 3 }}>
        <Typography variant="h5" sx={{ fontWeight: 750 }}>
          Access denied
        </Typography>
        <Alert severity="warning">
          You are authenticated, but you do not have permission to view this page.
        </Alert>
      </Stack>
    </Paper>
  );
}

