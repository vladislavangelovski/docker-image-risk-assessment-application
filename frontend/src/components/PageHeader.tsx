import React from "react";
import { Box, Stack, Typography } from "@mui/material";

export function PageHeader({
  title,
  subtitle,
  icon,
  actions
}: {
  title: string;
  subtitle?: string;
  icon?: React.ReactNode;
  actions?: React.ReactNode;
}) {
  return (
    <Stack
      direction={{ xs: "column", md: "row" }}
      spacing={2}
      alignItems={{ xs: "flex-start", md: "center" }}
      justifyContent="space-between"
      sx={{ mb: 3 }}
    >
      <Stack direction="row" spacing={1.5} alignItems="center">
        {icon && (
          <Box
            sx={{
              width: 44,
              height: 44,
              borderRadius: 3,
              display: "grid",
              placeItems: "center",
              background:
                "linear-gradient(135deg, rgba(30, 168, 150, 0.18), rgba(246, 176, 66, 0.12))",
              border: "1px solid rgba(17, 26, 47, 0.08)"
            }}
          >
            {icon}
          </Box>
        )}
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 750, letterSpacing: "-0.02em" }}>
            {title}
          </Typography>
          {subtitle && (
            <Typography color="text.secondary" sx={{ mt: 0.25 }}>
              {subtitle}
            </Typography>
          )}
        </Box>
      </Stack>
      {actions}
    </Stack>
  );
}

