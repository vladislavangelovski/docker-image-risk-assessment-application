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

const drawerWidth = 312;
const drawerWidthMobile = 272;

const baseNavItems = [
  { label: "Dashboard", to: "/", icon: <DashboardRoundedIcon />, end: true },
  { label: "Image Risk", to: "/assess", icon: <ShieldRoundedIcon /> },
  { label: "Assess Compose", to: "/assess/compose", icon: <DescriptionRoundedIcon /> },
  { label: "CVE Lookup", to: "/cves", icon: <BugReportRoundedIcon /> },
  { label: "Scan History", to: "/scans", icon: <ReceiptLongRoundedIcon /> }
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
              borderRadius: 3.5,
              mx: 1.1,
              my: 0.35,
              transition:
                "transform var(--motion-fast) var(--ease-standard), background-color var(--motion-fast) var(--ease-standard), box-shadow var(--motion-fast) var(--ease-standard)",
              "&.Mui-selected": {
                backgroundColor: "rgba(168, 84, 47, 0.2)",
                boxShadow: "inset 0 0 0 1px rgba(168, 84, 47, 0.24)"
              },
              "&:hover": {
                transform: "translateX(2px)"
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
      <Stack spacing={0.8} sx={{ px: 2.75, pt: 3, pb: 2.25 }}>
        <Typography className="kicker">Risk Desk</Typography>
        <Typography variant="h4" sx={{ lineHeight: 0.95 }}>
          Docker Image Risk Assessment
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Exposure intelligence with traceable evidence.
        </Typography>
      </Stack>
      <Divider sx={{ mx: 2.2 }} />
      <List sx={{ px: 1, py: 1 }}>
        {navItems.map((item) => (
          <NavItem key={item.to} {...item} />
        ))}
      </List>
      <Box sx={{ flexGrow: 1 }} />
      <Box sx={{ px: 2.75, pb: 2.5 }}>
        <Divider sx={{ mb: 2 }} />
        <Typography className="kicker" sx={{ mb: 0.6, fontSize: "0.66rem" }}>
          Contract boundary
        </Typography>
        <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
          Gateway-first frontend
        </Typography>
        <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 1.1 }}>
          © {new Date().getFullYear()} Risk Console
        </Typography>
      </Box>
    </Box>
  );

  return (
    <Box className="editorial-shell" sx={{ display: "flex", minHeight: "100vh" }}>
      <AppBar
        position="fixed"
        elevation={0}
        sx={{
          width: { md: `calc(100% - ${drawerWidth}px)` },
          ml: { md: `${drawerWidth}px` },
          backgroundColor:
            mode === "dark" ? "rgba(16, 21, 30, 0.72)" : "rgba(252, 245, 237, 0.7)",
          borderBottom:
            mode === "dark"
              ? "1px solid rgba(243, 227, 208, 0.1)"
              : "1px solid rgba(102, 78, 60, 0.14)",
          backdropFilter: "blur(16px)",
          color: "inherit"
        }}
      >
        <Toolbar
          sx={{
            display: "flex",
            justifyContent: "space-between",
            gap: 2,
            minHeight: { xs: 66, sm: 74 }
          }}
        >
          <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 0, overflow: "hidden" }}>
            {!isDesktop && (
              <IconButton
                onClick={toggleDrawer}
                aria-label="Open navigation"
                sx={{
                  backgroundColor: "rgba(168, 84, 47, 0.12)",
                  "&:hover": { backgroundColor: "rgba(168, 84, 47, 0.2)" }
                }}
              >
                <MenuRoundedIcon />
              </IconButton>
            )}
            <Box sx={{ minWidth: 0, maxWidth: { sm: "min(56vw, 540px)" } }}>
              <Typography className="kicker" sx={{ fontSize: "0.63rem", mb: 0.4, display: { xs: "none", sm: "inline-block" } }}>
                Operations view
              </Typography>
              <Typography
                variant="subtitle1"
                sx={{ fontWeight: 700, display: { xs: "none", lg: "block" } }}
                noWrap
              >
                Vulnerability and Misconfiguration Workbench
              </Typography>
              <Typography variant="subtitle2" sx={{ fontWeight: 700, display: { xs: "block", lg: "none" } }}>
                Risk Workbench
              </Typography>
            </Box>
          </Stack>
          <Stack direction="row" spacing={{ xs: 0.45, sm: 0.75 }} alignItems="center">
            <Tooltip title={mode === "dark" ? "Switch to light mode" : "Switch to dark mode"}>
              <IconButton
                onClick={toggleMode}
                aria-label="Toggle color mode"
                sx={{
                  backgroundColor: "rgba(168, 84, 47, 0.12)",
                  "&:hover": { backgroundColor: "rgba(168, 84, 47, 0.2)" }
                }}
              >
                {mode === "dark" ? <LightModeRoundedIcon /> : <DarkModeRoundedIcon />}
              </IconButton>
            </Tooltip>
            <Tooltip title="About the console">
              <IconButton
                onClick={() => setAboutOpen(true)}
                aria-label="About"
                sx={{
                  backgroundColor: "rgba(168, 84, 47, 0.12)",
                  "&:hover": { backgroundColor: "rgba(168, 84, 47, 0.2)" }
                }}
              >
                <InfoOutlinedIcon />
              </IconButton>
            </Tooltip>
            {auth.initialized &&
              (auth.authenticated ? (
                <>
                  <Typography
                    variant="body2"
                    color="text.secondary"
                    sx={{ display: { xs: "none", sm: "block" }, maxWidth: 240 }}
                    noWrap
                  >
                    {auth.user?.username || auth.user?.email || "Signed in"}
                  </Typography>
                  <Button variant="outlined" size="small" onClick={auth.logout}>
                    <Box component="span" sx={{ display: { xs: "none", sm: "inline" } }}>
                      Sign out
                    </Box>
                    <Box component="span" sx={{ display: { xs: "inline", sm: "none" } }}>
                      Out
                    </Box>
                  </Button>
                </>
              ) : (
                <Button variant="contained" size="small" onClick={auth.login}>
                  <Box component="span" sx={{ display: { xs: "none", sm: "inline" } }}>
                    Sign in
                  </Box>
                  <Box component="span" sx={{ display: { xs: "inline", sm: "none" } }}>
                    Enter
                  </Box>
                </Button>
              ))}
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
                    ? "1px solid rgba(243, 227, 208, 0.12)"
                    : "1px solid rgba(111, 86, 66, 0.16)",
                backgroundColor:
                  mode === "dark" ? "rgba(18, 23, 32, 0.68)" : "rgba(255, 248, 238, 0.68)",
                backdropFilter: "blur(14px)"
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
                width: { xs: drawerWidthMobile, sm: drawerWidth },
                boxSizing: "border-box",
                backgroundColor:
                  mode === "dark" ? "rgba(18, 23, 32, 0.96)" : "rgba(255, 248, 238, 0.96)"
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
          pt: 11,
          pb: 7
        }}
      >
        <Container maxWidth="xl">
          <React.Suspense
            fallback={
              <Stack spacing={2} alignItems="center" sx={{ py: 10 }}>
                <CircularProgress />
                <Typography variant="body2" color="text.secondary">
                  Loading workspace...
                </Typography>
              </Stack>
            }
          >
            <Outlet />
          </React.Suspense>
        </Container>
      </Box>

      <Dialog open={aboutOpen} onClose={() => setAboutOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>About this console</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ py: 1 }}>
            <Typography variant="body2" color="text.secondary">
              This workspace combines image and compose assessment, CVE context, scan inspection,
              and follow-up analysis in one operational UI.
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
