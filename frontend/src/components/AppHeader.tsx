import React from "react";
import {
  AppBar,
  Box,
  Chip,
  Stack,
  Toolbar,
  Typography
} from "@mui/material";
import { NavLink } from "react-router-dom";
import ShieldIcon from "@mui/icons-material/Shield";
import { API_BASE_URL } from "../api/client";

const navItems = [
  { label: "Dashboard", to: "/", end: true },
  { label: "Assess", to: "/assess" },
  { label: "QA", to: "/qa" },
  { label: "CVE Lookup", to: "/cves" }
];

export function AppHeader() {
  return (
    <AppBar
      position="sticky"
      elevation={0}
      sx={{
        background: "rgba(248, 245, 240, 0.92)",
        borderBottom: "1px solid rgba(17, 26, 47, 0.08)",
        backdropFilter: "blur(12px)"
      }}
    >
      <Toolbar sx={{ justifyContent: "space-between", flexWrap: "wrap", gap: 2 }}>
        <Stack direction="row" spacing={2} alignItems="center">
          <ShieldIcon sx={{ color: "var(--mint-500)" }} />
          <Box>
            <Typography variant="h6" sx={{ fontWeight: 600 }}>
              Risk Assessment Console
            </Typography>
            <Typography variant="caption" sx={{ color: "var(--ink-700)" }}>
              Gateway-first visibility for container exposure
            </Typography>
          </Box>
        </Stack>
        <Stack
          direction={{ xs: "column", md: "row" }}
          spacing={1}
          alignItems={{ xs: "flex-start", md: "center" }}
          sx={{ flexWrap: "wrap" }}
        >
          <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
            {navItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  isActive ? "nav-link active" : "nav-link"
                }
              >
                {item.label}
              </NavLink>
            ))}
          </Stack>
          <Chip
            label={`Gateway: ${API_BASE_URL}`}
            variant="outlined"
            sx={{ borderColor: "rgba(17, 26, 47, 0.18)", color: "var(--ink-800)" }}
          />
        </Stack>
      </Toolbar>
    </AppBar>
  );
}
