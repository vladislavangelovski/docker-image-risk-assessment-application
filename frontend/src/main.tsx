import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import "./index.css";
import { CssBaseline, ThemeProvider, createTheme } from "@mui/material";

const theme = createTheme({
  palette: {
    mode: "light",
    primary: {
      main: "#1ea896"
    },
    secondary: {
      main: "#f6b042"
    },
    error: {
      main: "#e4575b"
    },
    background: {
      default: "#f7f3ea",
      paper: "#ffffff"
    }
  },
  typography: {
    fontFamily: '"IBM Plex Sans", "Segoe UI", sans-serif',
    h1: {
      fontFamily: '"Space Grotesk", "IBM Plex Sans", sans-serif',
      fontWeight: 600,
      letterSpacing: "-0.02em"
    },
    h2: {
      fontFamily: '"Space Grotesk", "IBM Plex Sans", sans-serif',
      fontWeight: 600,
      letterSpacing: "-0.02em"
    },
    h3: {
      fontFamily: '"Space Grotesk", "IBM Plex Sans", sans-serif',
      fontWeight: 600
    },
    h4: {
      fontFamily: '"Space Grotesk", "IBM Plex Sans", sans-serif',
      fontWeight: 600
    }
  },
  shape: {
    borderRadius: 16
  },
  components: {
    MuiButton: {
      styleOverrides: {
        root: {
          textTransform: "none",
          borderRadius: 999,
          fontWeight: 600
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
          fontWeight: 600
        }
      }
    }
  }
});

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <App />
    </ThemeProvider>
  </React.StrictMode>
);
