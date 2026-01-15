import React from "react";
import type { PaletteMode } from "@mui/material";

export interface ColorModeContextValue {
  mode: PaletteMode;
  setMode: (mode: PaletteMode) => void;
  toggleMode: () => void;
}

export const ColorModeContext = React.createContext<ColorModeContextValue | null>(null);

export function useColorMode(): ColorModeContextValue {
  const value = React.useContext(ColorModeContext);
  if (!value) {
    throw new Error("useColorMode must be used within AppProviders.");
  }
  return value;
}

