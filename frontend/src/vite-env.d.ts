/// <reference types="vite/client" />

declare global {
  interface Window {
    __RISK_CONSOLE_CONFIG__?: {
      API_BASE_URL?: string;
      AUTH_BASE_URL?: string;
      AUTH_REALM?: string;
      AUTH_CLIENT_ID?: string;
    };
  }
}

export {};
