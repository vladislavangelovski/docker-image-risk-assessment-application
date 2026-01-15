import React from "react";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import { AppShell } from "./components/AppShell";

const Dashboard = React.lazy(() =>
  import("./pages/Dashboard").then((module) => ({ default: module.Dashboard }))
);
const ImageAssessment = React.lazy(() =>
  import("./pages/ImageAssessment").then((module) => ({ default: module.ImageAssessment }))
);
const QaCenter = React.lazy(() =>
  import("./pages/QaCenter").then((module) => ({ default: module.QaCenter }))
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

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppShell />}>
          <Route path="/" element={<Dashboard />} />
          <Route path="/assess" element={<ImageAssessment />} />
          <Route path="/qa" element={<QaCenter />} />
          <Route path="/cves" element={<CveLookup />} />
          <Route path="/scans" element={<ScanViewer />} />
          <Route path="/admin/embeddings" element={<AdminEmbeddings />} />
          <Route path="*" element={<NotFound />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
