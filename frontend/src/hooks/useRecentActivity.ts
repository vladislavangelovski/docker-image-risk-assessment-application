import React from "react";
import { useAuth } from "../auth/useAuth";
import { useLocalStorageState } from "./useLocalStorageState";

export type ActivityKind =
  | "ASSESS_IMAGE"
  | "ASSESS_COMPOSE"
  | "CVE_LOOKUP"
  | "SCAN_VIEW"
  | "EMBEDDINGS_INDEX"
  | "EMBEDDINGS_SEARCH";

export interface ActivityItem {
  id: string;
  kind: ActivityKind;
  label: string;
  description?: string;
  href: string;
  timestamp: number;
}

const STORAGE_KEY_PREFIX = "risk-console.recentActivity";
const MAX_ITEMS = 24;

function makeId() {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function useRecentActivity() {
  const auth = useAuth();
  const userKey = React.useMemo(() => {
    const user =
      auth.user?.username || auth.user?.email || auth.user?.name || (auth.authenticated ? "user" : "anonymous");
    return encodeURIComponent(user.trim().toLowerCase());
  }, [auth.authenticated, auth.user?.email, auth.user?.name, auth.user?.username]);
  const storageKey = `${STORAGE_KEY_PREFIX}.${userKey}`;

  const [items, setItems] = useLocalStorageState<ActivityItem[]>(storageKey, []);

  const addActivity = React.useCallback(
    (activity: Omit<ActivityItem, "id" | "timestamp"> & Partial<Pick<ActivityItem, "timestamp">>) => {
      setItems((prev) => {
        const now = activity.timestamp ?? Date.now();
        const next: ActivityItem = { ...activity, id: makeId(), timestamp: now };

        const deduped = prev.filter((item) => item.href !== next.href);
        return [next, ...deduped].slice(0, MAX_ITEMS);
      });
    },
    [setItems]
  );

  const clearActivity = React.useCallback(() => setItems([]), [setItems]);

  return { items, addActivity, clearActivity } as const;
}
