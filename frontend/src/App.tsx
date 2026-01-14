import React from "react";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import { Box, Container, Typography } from "@mui/material";
import { AppHeader } from "./components/AppHeader";
import { Dashboard } from "./pages/Dashboard";
import { ImageAssessment } from "./pages/ImageAssessment";
import { QaCenter } from "./pages/QaCenter";
import { CveLookup } from "./pages/CveLookup";
import { ScanViewer } from "./pages/ScanViewer";
import { AdminEmbeddings } from "./pages/AdminEmbeddings";

export default function App() {
  return (
    <BrowserRouter>
      <AppHeader />
      <Box className="app-shell">
        <Container maxWidth="lg">
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/assess" element={<ImageAssessment />} />
            <Route path="/qa" element={<QaCenter />} />
            <Route path="/cves" element={<CveLookup />} />
            <Route path="/scans" element={<ScanViewer />} />
            <Route path="/admin/embeddings" element={<AdminEmbeddings />} />
          </Routes>
        </Container>
      </Box>
      <Box component="footer" sx={{ pb: 4 }}>
        <Container maxWidth="lg">
          <Typography variant="caption" color="text.secondary">
            Gateway-only frontend. Configure VITE_API_BASE_URL in the frontend environment or .env.
          </Typography>
        </Container>
      </Box>
    </BrowserRouter>
  );
}
