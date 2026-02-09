import React from "react";
import {
  AppBar,
  Box,
  Button,
  CircularProgress,
  Container,
  Dialog,
  DialogContent,
  DialogTitle,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Stack,
  Toolbar,
  Tooltip,
  Typography,
  useMediaQuery
} from "@mui/material";
import { useTheme } from "@mui/material/styles";
import { NavLink, Outlet } from "react-router-dom";
import DashboardRoundedIcon from "@mui/icons-material/DashboardRounded";
import ShieldRoundedIcon from "@mui/icons-material/ShieldRounded";
import DescriptionRoundedIcon from "@mui/icons-material/DescriptionRounded";
import PsychologyRoundedIcon from "@mui/icons-material/PsychologyRounded";
import BugReportRoundedIcon from "@mui/icons-material/BugReportRounded";
import ReceiptLongRoundedIcon from "@mui/icons-material/ReceiptLongRounded";
import MenuRoundedIcon from "@mui/icons-material/MenuRounded";
import LightModeRoundedIcon from "@mui/icons-material/LightModeRounded";
import DarkModeRoundedIcon from "@mui/icons-material/DarkModeRounded";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import AdminPanelSettingsRoundedIcon from "@mui/icons-material/AdminPanelSettingsRounded";
import { useColorMode } from "../theme/colorMode";
import { API_BASE_URL } from "../api/client";
import { useAuth } from "../auth/useAuth";

const drawerWidth = 288;

const baseNavItems = [
  { label: "Dashboard", to: "/", icon: <DashboardRoundedIcon />, end: true },
  { label: "Assess Image", to: "/assess", icon: <ShieldRoundedIcon /> },
  { label: "Assess Compose", to: "/assess/compose", icon: <DescriptionRoundedIcon /> },
  { label: "QA Center", to: "/qa", icon: <PsychologyRoundedIcon /> },
  { label: "CVE Lookup", to: "/cves", icon: <BugReportRoundedIcon /> },
  { label: "Scans", to: "/scans", icon: <ReceiptLongRoundedIcon /> }
];

function NavItem({
  label,
  to,
  icon,
  end
}: {
  label: string;
  to: string;
  icon: React.ReactNode;
  end?: boolean;
}) {
  return (
    <ListItem disablePadding>
      <NavLink
        to={to}
        end={end}
        style={{ width: "100%", color: "inherit", display: "block" }}
      >
        {({ isActive }) => (
          <ListItemButton
            selected={isActive}
            sx={{
              borderRadius: 3,
              mx: 1,
              my: 0.25,
              "&.Mui-selected": {
                backgroundColor: "rgba(30, 168, 150, 0.14)"
              }
            }}
          >
            <ListItemIcon sx={{ minWidth: 42 }}>{icon}</ListItemIcon>
            <ListItemText
              primary={label}
              primaryTypographyProps={{ fontWeight: 600, fontSize: 14 }}
            />
          </ListItemButton>
        )}
      </NavLink>
    </ListItem>
  );
}

export function AppShell() {
  const theme = useTheme();
  const isDesktop = useMediaQuery(theme.breakpoints.up("md"));
  const [mobileOpen, setMobileOpen] = React.useState(false);
  const [aboutOpen, setAboutOpen] = React.useState(false);
  const { mode, toggleMode } = useColorMode();
  const auth = useAuth();

  const toggleDrawer = () => setMobileOpen((prev) => !prev);

  const navItems = React.useMemo(() => {
    if (!auth.isAdmin) {
      return baseNavItems;
    }
    return [
      ...baseNavItems,
      {
        label: "Admin: Embeddings",
        to: "/admin/embeddings",
        icon: <AdminPanelSettingsRoundedIcon />
      }
    ];
  }, [auth.isAdmin]);

  const drawer = (
    <Box sx={{ height: "100%", display: "flex", flexDirection: "column" }}>
      <Stack spacing={0.5} sx={{ px: 2.5, py: 2.25 }}>
        <Typography variant="subtitle2" sx={{ letterSpacing: "0.08em", opacity: 0.7 }}>
          RISK CONSOLE
        </Typography>
        <Typography variant="h6" sx={{ fontWeight: 700, lineHeight: 1.1 }}>
          Docker Image Risk Assessment
        </Typography>
        <Typography variant="caption" color="text.secondary">
          Evidence-backed exposure insights
        </Typography>
      </Stack>
      <Divider sx={{ mx: 2 }} />
      <List sx={{ px: 1, py: 1 }}>
        {navItems.map((item) => (
          <NavItem key={item.to} {...item} />
        ))}
      </List>
      <Box sx={{ flexGrow: 1 }} />
      <Box sx={{ px: 2.5, pb: 2.5 }}>
        <Divider sx={{ mb: 2 }} />
        <Typography variant="caption" color="text.secondary">
          © {new Date().getFullYear()} Risk Assessment Console
        </Typography>
      </Box>
    </Box>
  );

  return (
    <Box sx={{ display: "flex", minHeight: "100vh" }}>
      <AppBar
        position="fixed"
        elevation={0}
        sx={{
          backgroundColor:
            mode === "dark" ? "rgba(11, 16, 32, 0.78)" : "rgba(248, 245, 240, 0.82)",
          borderBottom:
            mode === "dark"
              ? "1px solid rgba(210, 220, 255, 0.12)"
              : "1px solid rgba(17, 26, 47, 0.08)",
          backdropFilter: "blur(16px)",
          color: "inherit"
        }}
      >
        <Toolbar
          sx={{
            display: "flex",
            justifyContent: "space-between",
            gap: 2,
            minHeight: 72
          }}
        >
          <Stack direction="row" spacing={1} alignItems="center">
            {!isDesktop && (
              <IconButton onClick={toggleDrawer} aria-label="Open navigation">
                <MenuRoundedIcon />
              </IconButton>
            )}
            <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
              Risk Assessment Console
            </Typography>
          </Stack>
          <Stack direction="row" spacing={1} alignItems="center">
            <Tooltip title={mode === "dark" ? "Switch to light mode" : "Switch to dark mode"}>
              <IconButton onClick={toggleMode} aria-label="Toggle color mode">
                {mode === "dark" ? <LightModeRoundedIcon /> : <DarkModeRoundedIcon />}
              </IconButton>
            </Tooltip>
            {auth.initialized &&
              (auth.authenticated ? (
                <>
                  <Typography
                    variant="body2"
                    color="text.secondary"
                    sx={{ display: { xs: "none", sm: "block" } }}
                  >
                    {auth.user?.username || auth.user?.email || "Signed in"}
                  </Typography>
                  <Button variant="outlined" size="small" onClick={auth.logout}>
                    Sign out
                  </Button>
                </>
              ) : (
                <Button variant="contained" size="small" onClick={auth.login}>
                  Sign in
                </Button>
              ))}
            <Tooltip title="About">
              <IconButton onClick={() => setAboutOpen(true)} aria-label="About">
                <InfoOutlinedIcon />
              </IconButton>
            </Tooltip>
          </Stack>
        </Toolbar>
      </AppBar>

      <Box component="nav" sx={{ width: { md: drawerWidth }, flexShrink: { md: 0 } }}>
        {isDesktop ? (
          <Drawer
            variant="permanent"
            open
            sx={{
              "& .MuiDrawer-paper": {
                width: drawerWidth,
                boxSizing: "border-box",
                borderRight:
                  mode === "dark"
                    ? "1px solid rgba(210, 220, 255, 0.12)"
                    : "1px solid rgba(17, 26, 47, 0.08)",
                backgroundColor:
                  mode === "dark"
                    ? "rgba(13, 18, 34, 0.72)"
                    : "rgba(255, 255, 255, 0.74)",
                backdropFilter: "blur(18px)"
              }
            }}
          >
            {drawer}
          </Drawer>
        ) : (
          <Drawer
            variant="temporary"
            open={mobileOpen}
            onClose={toggleDrawer}
            ModalProps={{ keepMounted: true }}
            sx={{
              "& .MuiDrawer-paper": {
                width: drawerWidth,
                boxSizing: "border-box",
                backgroundColor:
                  mode === "dark"
                    ? "rgba(13, 18, 34, 0.92)"
                    : "rgba(255, 255, 255, 0.92)"
              }
            }}
          >
            {drawer}
          </Drawer>
        )}
      </Box>

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          width: { md: `calc(100% - ${drawerWidth}px)` },
          pt: 10,
          pb: 6
        }}
      >
        <Container maxWidth="xl">
          <React.Suspense
            fallback={
              <Stack spacing={2} alignItems="center" sx={{ py: 10 }}>
                <CircularProgress />
                <Typography variant="body2" color="text.secondary">
                  Loading…
                </Typography>
              </Stack>
            }
          >
            <Outlet />
          </React.Suspense>
        </Container>
      </Box>

      <Dialog open={aboutOpen} onClose={() => setAboutOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>About</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ py: 1 }}>
            <Typography variant="body2" color="text.secondary">
              Production-ready console for Docker image assessments, CVE lookups, scan inspection,
              and QA with citations.
            </Typography>
            <Box>
              <Typography variant="subtitle2">API Base URL</Typography>
              <Typography variant="body2" color="text.secondary">
                {API_BASE_URL}
              </Typography>
            </Box>
            <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
              <Button
                variant="outlined"
                href={`${API_BASE_URL}/swagger-ui.html`}
                target="_blank"
                rel="noreferrer"
              >
                Open API docs
              </Button>
              <Button variant="text" onClick={() => setAboutOpen(false)}>
                Close
              </Button>
            </Stack>
          </Stack>
        </DialogContent>
      </Dialog>
    </Box>
  );
}
