import React from "react";
import { Button, Paper, Stack, Typography } from "@mui/material";
import { Link as RouterLink } from "react-router-dom";

export function NotFound() {
  return (
    <Paper className="hero-card">
      <Stack spacing={2} sx={{ p: 4 }}>
        <Typography className="kicker">Routing</Typography>
        <Typography variant="h3" sx={{ lineHeight: 0.94 }}>
          Page not found
        </Typography>
        <Typography color="text.secondary">
          The requested page does not exist or the route has changed.
        </Typography>
        <Button variant="contained" component={RouterLink} to="/">
          Go to dashboard
        </Button>
      </Stack>
    </Paper>
  );
}
