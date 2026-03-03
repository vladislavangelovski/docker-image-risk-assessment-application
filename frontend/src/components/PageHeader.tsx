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
      direction={{ xs: "column", lg: "row" }}
      spacing={2}
      alignItems={{ xs: "flex-start", lg: "center" }}
      justifyContent="space-between"
      sx={{ mb: 3.5 }}
    >
      <Stack direction="row" spacing={1.5} alignItems="center" sx={{ minWidth: 0 }}>
        {icon && (
          <Box
            sx={{
              width: { xs: 44, sm: 50 },
              height: { xs: 44, sm: 50 },
              borderRadius: 3.5,
              display: "grid",
              placeItems: "center",
              background:
                "linear-gradient(140deg, rgba(168, 84, 47, 0.22), rgba(0, 107, 107, 0.16))",
              border: "1px solid var(--card-border)",
              boxShadow: "0 10px 20px rgba(46, 31, 20, 0.14)",
              flexShrink: 0
            }}
          >
            {icon}
          </Box>
        )}
        <Box sx={{ minWidth: 0 }}>
          <Typography className="kicker" sx={{ mb: 0.5, fontSize: { xs: "0.64rem", sm: "0.72rem" } }}>
            Operations workspace
          </Typography>
          <Typography
            variant="h3"
            sx={{ lineHeight: 0.95, fontSize: { xs: "1.72rem", sm: "2.5rem", lg: "3rem" } }}
          >
            {title}
          </Typography>
          {subtitle && (
            <Typography color="text.secondary" sx={{ mt: 0.7, maxWidth: 760, fontSize: { xs: "0.92rem", sm: "1rem" } }}>
              {subtitle}
            </Typography>
          )}
        </Box>
      </Stack>
      {actions}
    </Stack>
  );
}
