import { createTheme } from "@mui/material/styles";
import type { PaletteMode } from "@mui/material";

export function buildTheme(mode: PaletteMode) {
  const isDark = mode === "dark";

  return createTheme({
    palette: {
      mode,
      primary: { main: isDark ? "#f3b47b" : "#8f3f24" },
      secondary: { main: isDark ? "#7bc8be" : "#006b6b" },
      error: { main: isDark ? "#ff8675" : "#b03a2f" },
      warning: { main: isDark ? "#f1c67a" : "#b8791c" },
      success: { main: isDark ? "#8fd2a0" : "#2b7e57" },
      info: { main: isDark ? "#9ebee5" : "#2f5f9f" },
      background: isDark
        ? { default: "#101620", paper: "rgba(25, 32, 44, 0.86)" }
        : { default: "#f4ecdf", paper: "rgba(255, 251, 246, 0.88)" },
      text: isDark
        ? { primary: "#f3ece4", secondary: "rgba(233, 221, 209, 0.76)" }
        : { primary: "#1f1a16", secondary: "rgba(46, 35, 27, 0.72)" },
      divider: isDark ? "rgba(246, 232, 220, 0.12)" : "rgba(101, 80, 60, 0.15)"
    },
    typography: {
      fontFamily: '"Manrope", "Segoe UI", sans-serif',
      h1: {
        fontFamily: '"Cormorant Garamond", "Times New Roman", serif',
        fontWeight: 700,
        letterSpacing: "-0.015em"
      },
      h2: {
        fontFamily: '"Cormorant Garamond", "Times New Roman", serif',
        fontWeight: 700,
        letterSpacing: "-0.015em"
      },
      h3: {
        fontFamily: '"Cormorant Garamond", "Times New Roman", serif',
        fontWeight: 700
      },
      h4: {
        fontFamily: '"Cormorant Garamond", "Times New Roman", serif',
        fontWeight: 700
      },
      h5: {
        fontFamily: '"Cormorant Garamond", "Times New Roman", serif',
        fontWeight: 700
      },
      h6: {
        fontFamily: '"Cormorant Garamond", "Times New Roman", serif',
        fontWeight: 700
      },
      subtitle1: {
        letterSpacing: "0.01em"
      },
      subtitle2: {
        letterSpacing: "0.01em"
      },
      button: {
        fontFamily: '"Manrope", "Segoe UI", sans-serif',
        letterSpacing: "0.01em"
      }
    },
    shape: { borderRadius: 20 },
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          body: {
            minHeight: "100vh",
            textRendering: "optimizeLegibility"
          }
        }
      },
      MuiButton: {
        styleOverrides: {
          root: {
            textTransform: "none",
            borderRadius: 999,
            fontWeight: 650,
            paddingInline: 18,
            minHeight: 42
          },
          contained: {
            boxShadow: isDark ? "0 10px 30px rgba(0, 0, 0, 0.35)" : "0 12px 28px rgba(103, 63, 38, 0.18)"
          }
        }
      },
      MuiPaper: {
        styleOverrides: {
          root: {
            backgroundImage: "none",
            borderColor: isDark ? "rgba(245, 233, 220, 0.12)" : "rgba(93, 69, 52, 0.16)"
          }
        }
      },
      MuiChip: {
        styleOverrides: {
          root: {
            fontWeight: 650,
            borderRadius: 999
          }
        }
      },
      MuiOutlinedInput: {
        styleOverrides: {
          root: {
            borderRadius: 14,
            backgroundColor: isDark ? "rgba(17, 24, 34, 0.72)" : "rgba(255, 251, 246, 0.82)"
          }
        }
      },
      MuiAlert: {
        styleOverrides: {
          root: {
            borderRadius: 14
          }
        }
      },
      MuiTableCell: {
        styleOverrides: {
          head: {
            fontWeight: 700
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
