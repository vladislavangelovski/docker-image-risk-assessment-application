import React from "react";
import { useLocalStorageState } from "./useLocalStorageState";

export type ActivityKind =
  | "ASSESS_IMAGE"
  | "QA_QUESTION"
  | "QA_CLAIM"
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

const STORAGE_KEY = "risk-console.recentActivity";
const MAX_ITEMS = 24;

function makeId() {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function useRecentActivity() {
  const [items, setItems] = useLocalStorageState<ActivityItem[]>(STORAGE_KEY, []);

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

