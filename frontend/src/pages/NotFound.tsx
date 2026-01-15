import React from "react";
import { Button, Paper, Stack, Typography } from "@mui/material";
import { Link as RouterLink } from "react-router-dom";

export function NotFound() {
  return (
    <Paper className="section-card">
      <Stack spacing={2} sx={{ p: 4 }}>
        <Typography variant="h4">Page not found</Typography>
        <Typography color="text.secondary">
          The page you are looking for does not exist, or the link has changed.
        </Typography>
        <Button variant="contained" component={RouterLink} to="/">
          Go to dashboard
        </Button>
      </Stack>
    </Paper>
  );
}

