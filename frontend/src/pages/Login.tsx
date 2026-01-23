import React from "react";
import { Alert, Box, Button, CircularProgress, Paper, Stack, Typography } from "@mui/material";
import LockRoundedIcon from "@mui/icons-material/LockRounded";
import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "../auth/useAuth";

type LocationState = { from?: string };

const LOGIN_RETURN_KEY = "risk-console.loginFrom";

function normalizeReturnPath(value: string | null | undefined): string {
  const trimmed = (value || "").trim();
  if (!trimmed) return "/";
  if (!trimmed.startsWith("/")) return "/";
  return trimmed;
}

export function Login() {
  const auth = useAuth();
  const location = useLocation();
  const state = (location.state || {}) as LocationState;
  const from = normalizeReturnPath(state.from);

  React.useEffect(() => {
    sessionStorage.setItem(LOGIN_RETURN_KEY, from);
  }, [from]);

  if (!auth.initialized) {
    return (
      <Stack spacing={2} alignItems="center" sx={{ py: 12 }}>
        <CircularProgress />
        <Typography variant="body2" color="text.secondary">
          Loading authentication…
        </Typography>
      </Stack>
    );
  }

  if (auth.authenticated) {
    const stored = normalizeReturnPath(sessionStorage.getItem(LOGIN_RETURN_KEY));
    sessionStorage.removeItem(LOGIN_RETURN_KEY);
    return <Navigate to={stored} replace />;
  }

  return (
    <Box sx={{ maxWidth: 520, mx: "auto", py: { xs: 6, md: 10 } }}>
      <Paper className="section-card">
        <Stack spacing={3} sx={{ p: { xs: 3, md: 4 } }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <LockRoundedIcon sx={{ color: "var(--mint-500)" }} />
            <Typography variant="h5" sx={{ fontWeight: 750 }}>
              Sign in
            </Typography>
          </Stack>
          <Typography variant="body2" color="text.secondary">
            Authenticate to access the Risk Assessment Console.
          </Typography>
          {auth.error && <Alert severity="error">{auth.error}</Alert>}
          <Button
            variant="contained"
            size="large"
            onClick={() => {
              sessionStorage.setItem(LOGIN_RETURN_KEY, from);
              auth.login();
            }}
          >
            Sign in with Keycloak
          </Button>
          <Typography variant="caption" color="text.secondary">
            After signing in, you can grant admin access by assigning the <code>admin</code> realm
            role in Keycloak.
          </Typography>
        </Stack>
      </Paper>
    </Box>
  );
}
