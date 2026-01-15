/// <reference types="vite/client" />

declare global {
  interface Window {
    __RISK_CONSOLE_CONFIG__?: {
      API_BASE_URL?: string;
    };
  }
}

export {};
