import React from "react";
import { BrowserRouter, Navigate, Route, Routes, useLocation } from "react-router-dom";
import { CircularProgress, Stack, Typography } from "@mui/material";
import { AppShell } from "./components/AppShell";
import { useAuth } from "./auth/useAuth";
import { Login } from "./pages/Login";
import { Forbidden } from "./pages/Forbidden";

const Dashboard = React.lazy(() =>
  import("./pages/Dashboard").then((module) => ({ default: module.Dashboard }))
);
const ImageAssessment = React.lazy(() =>
  import("./pages/ImageAssessment").then((module) => ({ default: module.ImageAssessment }))
);
const ComposeAssessment = React.lazy(() =>
  import("./pages/ComposeAssessment").then((module) => ({ default: module.ComposeAssessment }))
);
const CveLookup = React.lazy(() =>
  import("./pages/CveLookup").then((module) => ({ default: module.CveLookup }))
);
const ScanViewer = React.lazy(() =>
  import("./pages/ScanViewer").then((module) => ({ default: module.ScanViewer }))
);
const AdminEmbeddings = React.lazy(() =>
  import("./pages/AdminEmbeddings").then((module) => ({ default: module.AdminEmbeddings }))
);
const NotFound = React.lazy(() =>
  import("./pages/NotFound").then((module) => ({ default: module.NotFound }))
);

function RequireAuth({ children }: { children: React.ReactElement }) {
  const auth = useAuth();
  const location = useLocation();

  if (!auth.initialized) {
    return (
      <Stack spacing={2} alignItems="center" sx={{ py: 12 }}>
        <CircularProgress />
        <Typography variant="body2" color="text.secondary">
          Initializing…
        </Typography>
      </Stack>
    );
  }

  if (!auth.authenticated) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: `${location.pathname}${location.search}${location.hash}` }}
      />
    );
  }

  return children;
}

function RequireAdmin({ children }: { children: React.ReactElement }) {
  const auth = useAuth();
  if (!auth.isAdmin) {
    return <Forbidden />;
  }
  return children;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />

        <Route
          element={
            <RequireAuth>
              <AppShell />
            </RequireAuth>
          }
        >
          <Route path="/" element={<Dashboard />} />
          <Route path="/assess" element={<ImageAssessment />} />
          <Route path="/assess/compose" element={<ComposeAssessment />} />
          <Route path="/cves" element={<CveLookup />} />
          <Route path="/scans" element={<ScanViewer />} />
          <Route
            path="/admin/embeddings"
            element={
              <RequireAdmin>
                <AdminEmbeddings />
              </RequireAdmin>
            }
          />
          <Route path="*" element={<NotFound />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
