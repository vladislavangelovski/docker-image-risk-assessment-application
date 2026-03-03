import React from "react";
import { Alert, Paper, Stack, Typography } from "@mui/material";

export function Forbidden() {
  return (
    <Paper className="hero-card">
      <Stack spacing={2} sx={{ p: 3 }}>
        <Typography className="kicker">Authorization</Typography>
        <Typography variant="h4" sx={{ lineHeight: 0.96 }}>
          Access denied
        </Typography>
        <Alert severity="warning">
          You are signed in, but your role cannot access this route.
        </Alert>
      </Stack>
    </Paper>
  );
}
