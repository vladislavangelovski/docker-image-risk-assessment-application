import { createTheme } from "@mui/material/styles";
import type { PaletteMode } from "@mui/material";

export function buildTheme(mode: PaletteMode) {
  const isDark = mode === "dark";

  return createTheme({
    palette: {
      mode,
      primary: { main: "#1ea896" },
      secondary: { main: "#f6b042" },
      error: { main: "#e4575b" },
      background: isDark
        ? { default: "#0b1020", paper: "rgba(19, 28, 48, 0.86)" }
        : { default: "#f7f3ea", paper: "#ffffff" }
    },
    typography: {
      fontFamily: '"IBM Plex Sans", "Segoe UI", system-ui, sans-serif',
      h1: {
        fontFamily: '"Space Grotesk", "IBM Plex Sans", system-ui, sans-serif',
        fontWeight: 650,
        letterSpacing: "-0.02em"
      },
      h2: {
        fontFamily: '"Space Grotesk", "IBM Plex Sans", system-ui, sans-serif',
        fontWeight: 650,
        letterSpacing: "-0.02em"
      },
      h3: {
        fontFamily: '"Space Grotesk", "IBM Plex Sans", system-ui, sans-serif',
        fontWeight: 650
      },
      h4: {
        fontFamily: '"Space Grotesk", "IBM Plex Sans", system-ui, sans-serif',
        fontWeight: 650
      }
    },
    shape: { borderRadius: 16 },
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          body: {
            minHeight: "100vh"
          }
        }
      },
      MuiButton: {
        styleOverrides: {
          root: {
            textTransform: "none",
            borderRadius: 999,
            fontWeight: 650
          }
        }
      },
      MuiPaper: {
        styleOverrides: {
          root: {
            backgroundImage: "none"
          }
        }
      },
      MuiChip: {
        styleOverrides: {
          root: {
            fontWeight: 650
          }
        }
      },
      MuiTooltip: {
        defaultProps: {
          arrow: true
        }
      }
    }
  });
}

