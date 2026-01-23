import React from "react";
import Keycloak, { type KeycloakInstance, type KeycloakTokenParsed } from "keycloak-js";
import { API_BASE_URL } from "../api/client";
import { setAccessToken } from "./token";

type RiskTokenParsed = KeycloakTokenParsed & {
  preferred_username?: string;
  name?: string;
  email?: string;
  realm_access?: { roles?: string[] };
  roles?: string[] | string;
};

export type AuthUser = {
  username?: string;
  name?: string;
  email?: string;
};

export type AuthContextValue = {
  initialized: boolean;
  authenticated: boolean;
  token?: string;
  user?: AuthUser;
  roles: string[];
  isAdmin: boolean;
  error?: string;
  login: () => void;
  logout: () => void;
  hasRole: (role: string) => boolean;
};

export const AuthContext = React.createContext<AuthContextValue | undefined>(undefined);

function normalizeBaseUrl(value: string): string {
  const trimmed = value.trim();
  if (!trimmed) return "";
  return trimmed.endsWith("/") ? trimmed.slice(0, -1) : trimmed;
}

function readAuthConfig() {
  const runtime = window.__RISK_CONSOLE_CONFIG__;
  const authBaseUrl =
    normalizeBaseUrl(runtime?.AUTH_BASE_URL || import.meta.env.VITE_AUTH_BASE_URL || "") ||
    `${API_BASE_URL}/auth`;
  const realm = (runtime?.AUTH_REALM || import.meta.env.VITE_AUTH_REALM || "risk").trim();
  const clientId = (runtime?.AUTH_CLIENT_ID || import.meta.env.VITE_AUTH_CLIENT_ID || "risk-console")
    .trim();

  return { authBaseUrl, realm, clientId };
}

function extractRoles(tokenParsed: RiskTokenParsed | undefined): string[] {
  if (!tokenParsed) return [];
  const realmRoles = tokenParsed.realm_access?.roles ?? [];
  const directRoles = tokenParsed.roles;
  if (typeof directRoles === "string") {
    return [...realmRoles, ...directRoles.split(/[,\s]+/).map((r) => r.trim()).filter(Boolean)];
  }
  if (Array.isArray(directRoles)) {
    return [...realmRoles, ...directRoles.map((r) => r.trim()).filter(Boolean)];
  }
  return [...realmRoles];
}

function extractUser(tokenParsed: RiskTokenParsed | undefined): AuthUser {
  if (!tokenParsed) return {};
  return {
    username: tokenParsed.preferred_username,
    name: tokenParsed.name,
    email: tokenParsed.email
  };
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [initialized, setInitialized] = React.useState(false);
  const [authenticated, setAuthenticated] = React.useState(false);
  const [token, setToken] = React.useState<string | undefined>(undefined);
  const [roles, setRoles] = React.useState<string[]>([]);
  const [user, setUser] = React.useState<AuthUser | undefined>(undefined);
  const [error, setError] = React.useState<string | undefined>(undefined);
  const keycloakRef = React.useRef<KeycloakInstance | null>(null);
  const didInitRef = React.useRef(false);

  const syncFromKeycloak = React.useCallback((keycloak: KeycloakInstance) => {
    const isAuthenticated = Boolean(keycloak.authenticated);
    setAuthenticated(isAuthenticated);
    setToken(keycloak.token);
    setAccessToken(keycloak.token);

    const parsed = keycloak.tokenParsed as RiskTokenParsed | undefined;
    setRoles(extractRoles(parsed));
    setUser(extractUser(parsed));
  }, []);

  React.useEffect(() => {
    if (didInitRef.current) return;
    didInitRef.current = true;

    const { authBaseUrl, realm, clientId } = readAuthConfig();
    if (!authBaseUrl || !realm || !clientId) {
      setError("Missing auth configuration. Check AUTH_BASE_URL / AUTH_REALM / AUTH_CLIENT_ID.");
      setInitialized(true);
      return;
    }

    const keycloak = new Keycloak({ url: authBaseUrl, realm, clientId });
    keycloakRef.current = keycloak;

    keycloak
      .init({
        onLoad: "check-sso",
        pkceMethod: "S256",
        checkLoginIframe: false,
        silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`
      })
      .then(() => syncFromKeycloak(keycloak))
      .catch(() => {
        setError("Unable to initialize authentication. Is the auth server reachable?");
      })
      .finally(() => setInitialized(true));
  }, [syncFromKeycloak]);

  React.useEffect(() => {
    const keycloak = keycloakRef.current;
    if (!keycloak) return;

    const id = window.setInterval(() => {
      if (!keycloak.authenticated) return;
      keycloak
        .updateToken(60)
        .then(() => syncFromKeycloak(keycloak))
        .catch(() => {
          keycloak.clearToken();
          syncFromKeycloak(keycloak);
        });
    }, 10_000);

    return () => window.clearInterval(id);
  }, [syncFromKeycloak]);

  const login = React.useCallback(() => {
    const keycloak = keycloakRef.current;
    void keycloak?.login({ redirectUri: window.location.href });
  }, []);

  const logout = React.useCallback(() => {
    const keycloak = keycloakRef.current;
    void keycloak?.logout({ redirectUri: `${window.location.origin}/login` });
  }, []);

  const hasRole = React.useCallback(
    (role: string) => {
      const normalized = role.trim().toLowerCase();
      if (!normalized) return false;
      return roles.some((r) => r.toLowerCase() === normalized);
    },
    [roles]
  );

  const isAdmin = hasRole("admin");

  const value = React.useMemo<AuthContextValue>(
    () => ({
      initialized,
      authenticated,
      token,
      user,
      roles,
      isAdmin,
      error,
      login,
      logout,
      hasRole
    }),
    [authenticated, error, hasRole, initialized, isAdmin, login, logout, roles, token, user]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
