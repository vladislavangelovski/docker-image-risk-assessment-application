import React from "react";
import { Button, Paper, Stack, Typography } from "@mui/material";
import RefreshRoundedIcon from "@mui/icons-material/RefreshRounded";

export class ErrorBoundary extends React.Component<
  { children: React.ReactNode },
  { hasError: boolean; message?: string }
> {
  public constructor(props: { children: React.ReactNode }) {
    super(props);
    this.state = { hasError: false };
  }

  public static getDerivedStateFromError(error: unknown) {
    return {
      hasError: true,
      message: error instanceof Error ? error.message : "Unexpected error"
    };
  }

  public render() {
    if (this.state.hasError) {
      return (
        <Paper className="section-card" sx={{ maxWidth: 720, mx: "auto", mt: 10 }}>
          <Stack spacing={2} sx={{ p: 4 }}>
            <Typography variant="h5" sx={{ fontWeight: 750 }}>
              Something went wrong
            </Typography>
            <Typography color="text.secondary">
              {this.state.message ||
                "The UI hit an unexpected error. Refresh the page to continue."}
            </Typography>
            <Button
              variant="contained"
              startIcon={<RefreshRoundedIcon />}
              onClick={() => window.location.reload()}
              sx={{ alignSelf: "flex-start" }}
            >
              Refresh
            </Button>
          </Stack>
        </Paper>
      );
    }

    return this.props.children;
  }
}

