import React from "react";
import { Box } from "@mui/material";

export function Sparkline({
  values,
  width = 220,
  height = 54,
  color = "currentColor"
}: {
  values: number[];
  width?: number;
  height?: number;
  color?: string;
}) {
  const points = React.useMemo(() => {
    if (values.length === 0) return "";

    const min = Math.min(...values);
    const max = Math.max(...values);
    const range = max - min || 1;
    const dx = values.length === 1 ? 0 : width / (values.length - 1);

    return values
      .map((value, index) => {
        const x = index * dx;
        const y = height - (height * (value - min)) / range;
        return `${x.toFixed(2)},${y.toFixed(2)}`;
      })
      .join(" ");
  }, [height, values, width]);

  if (!points) return null;

  return (
    <Box
      component="svg"
      role="img"
      aria-label="Trend sparkline"
      width={width}
      height={height}
      viewBox={`0 0 ${width} ${height}`}
      sx={{ display: "block" }}
    >
      <polyline
        points={points}
        fill="none"
        stroke={color}
        strokeWidth="2.5"
        strokeLinejoin="round"
        strokeLinecap="round"
      />
    </Box>
  );
}

