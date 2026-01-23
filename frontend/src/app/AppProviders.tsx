import React from "react";
import { CssBaseline, ThemeProvider } from "@mui/material";
import type { PaletteMode } from "@mui/material";
import { useLocalStorageState } from "../hooks/useLocalStorageState";
import { ColorModeContext } from "../theme/colorMode";
import { buildTheme } from "../theme/buildTheme";
import { ErrorBoundary } from "../components/ErrorBoundary";
import { AuthProvider } from "../auth/AuthProvider";

const STORAGE_KEY = "risk-console.colorMode";

function getPreferredMode(): PaletteMode {
  if (typeof window === "undefined") {
    return "light";
  }

  const media = window.matchMedia?.("(prefers-color-scheme: dark)");
  return media?.matches ? "dark" : "light";
}

export function AppProviders({ children }: { children: React.ReactNode }) {
  const [mode, setMode] = useLocalStorageState<PaletteMode>(STORAGE_KEY, getPreferredMode);

  const colorMode = React.useMemo(
    () => ({
      mode,
      setMode,
      toggleMode: () => setMode((prev) => (prev === "light" ? "dark" : "light"))
    }),
    [mode, setMode]
  );

  const theme = React.useMemo(() => buildTheme(mode), [mode]);

  React.useEffect(() => {
    document.documentElement.dataset.theme = mode;
  }, [mode]);

  return (
    <ColorModeContext.Provider value={colorMode}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <ErrorBoundary>
          <AuthProvider>{children}</AuthProvider>
        </ErrorBoundary>
      </ThemeProvider>
    </ColorModeContext.Provider>
  );
}
